package server

import java.util.UUID

import akka.actor.{ActorSystem, Props}
import akka.stream.{ActorMaterializer, ActorMaterializerSettings, Supervision}
import akka.stream.scaladsl.{Flow, Source, Tcp}
import akka.stream.scaladsl.Tcp.{IncomingConnection, ServerBinding}
import akka.util.ByteString
import mixer.HouseAccount
import mixer.Transaction.Transaction
import play.api.libs.json.{Format, JsArray, JsError, JsNumber, JsObject, JsPath, JsResult, JsString, JsSuccess, JsValue, Json, JsonValidationError, Reads}
import server.MainSupport.{ClientAddresses, ClientTransaction}
import service.{ClientRepo, MixingService}

import scala.concurrent.{ExecutionContext, Future}
import pureconfig.loadConfig
import pureconfig.generic.auto._

import scala.util.{Success, Try}



object MainSupport{

  implicit val clientAddressCmdFmt: Format[ClientAddresses] = Json.format[ClientAddresses]
  implicit val clientTransactionCmdFmt: Format[ClientTransaction] = Json.format[ClientTransaction]

  sealed trait Command
  case class ClientAddresses(user: String, addresses: Seq[String]) extends Command
  case class ClientTransaction( user: String, houseAddress: String, amount: BigDecimal ) extends Command

  implicit object commandReads extends Reads[Command] {
    def reads(json: JsValue): JsResult[Command] = json match {
      case JsObject(mapObj) =>

        val perhapsClientAddress: Option[ClientAddresses] =
          for{
            JsString(user) <- mapObj.get("user")
            JsArray(addresses) <- mapObj.get("addresses")
          } yield{
            ClientAddresses(user, addresses.map(_.toString()))
          }

        val perhapsClientTransaction: Option[ClientTransaction] =
          for{
            JsString(user) <- mapObj.get("user")
            JsString(houseAddress) <- mapObj.get("houseAddress")
            JsNumber(amount) <- mapObj.get("amount")
          } yield{
            ClientTransaction( user, houseAddress, amount)
          }

        if( perhapsClientAddress.isDefined ) JsSuccess(perhapsClientAddress.get)
        else if (perhapsClientTransaction.isDefined ) JsSuccess(perhapsClientTransaction.get)
        else JsError(Seq(JsPath() -> Seq(JsonValidationError("error.invalid"))))

      case _ =>
        JsError(Seq(JsPath() -> Seq(JsonValidationError("error.invalid"))))
    }
  }

  def depositAddress = UUID.randomUUID()

  def cmd( s: String ): Either[String, Command] = {
    val ss = s.replaceAll("\\r", "")
    Try{Json.parse(ss)} match{
      case Success(jsValue) => jsValue.validate[Command] match {
        case JsSuccess(cmd, _) =>
          Right(cmd)
        case err =>
          Left(s"Invalid command $s: $err")
      }
      case err =>
        Left(s"Invalid command $err:")
    }
  }
}


object Main extends App {

  val decider: Supervision.Decider = {
    case err => {
      println(s"supervisor got $err")
      Supervision.Resume
    }
  }


  case class JobcoinConfig(server: Server, houseSeedAmount: BigDecimal, maxMixingTimeSeconds: Int, mixingFeeFactor: Double, jobcoinPrecision: Int)
  case class Server(host: String, port: Int)
  val config: JobcoinConfig = loadConfig[JobcoinConfig] match{
    case Left(ex) =>
      ex.toList.foreach{ println _ }
      throw new Exception("FATAL CONFIG error")
    case Right(config) =>
      config
  }

  implicit val system: ActorSystem = ActorSystem("JobcoinMixer")
  implicit val materializer = ActorMaterializer(ActorMaterializerSettings(system).withSupervisionStrategy(decider))
  implicit val ec: ExecutionContext = ExecutionContext.Implicits.global

  val clientRepo: ClientRepo = new ClientRepo
  val houseAccount = system.actorOf(Props(new HouseAccount(clientRepo, config)))
  val mixingService: MixingService = new MixingService(clientRepo, houseAccount, config)

  import akka.stream.scaladsl.Framing

  val connections: Source[IncomingConnection, Future[ServerBinding]] =
    Tcp().bind("127.0.0.1", 8888)

  connections.runForeach { connection =>

    val process = Flow[ByteString]
      .via(Framing.delimiter(ByteString("\n"), maximumFrameLength = 256, allowTruncation = true))
      .map( _.utf8String )
      .map(MainSupport.cmd)
      .mapAsync(8){ cmd =>
          cmd match{

            case Right(clientAddresses: ClientAddresses) =>
              Future{clientRepo.addAddress(clientAddresses.user, clientAddresses.addresses)}.map { ci =>
                s"Coins sent to ${ci.userHouseDepositAddress} will be mixed and sent to ${ci.userAddresses.mkString(",")}"
              }

            case Right(cmd: ClientTransaction) =>
              mixingService.mix(Transaction(cmd.user, cmd.houseAddress, cmd.amount, Seq.empty, Seq.empty)).map{ mixedTransaction =>
                val res = mixedTransaction.outputs.map{
                  case( address, amt) =>
                    s"$amt sent to $address"
                }

                res.mkString(",")
              }

            case Left(err) =>
              Future{s"Bad cmd $err"}
          }

      }
      .map{ res => ByteString(s"$res\n") }
        .recover{
          case err => ByteString(s"error $err")
        }

    connection.handleWith( process )
  }
}
