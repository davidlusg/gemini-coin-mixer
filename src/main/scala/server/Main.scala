package server

import java.util.UUID

import akka.actor.{ActorSystem, Props}
import akka.stream.{ActorMaterializer, ActorMaterializerSettings, Supervision}
import akka.stream.scaladsl.{Flow, Source, Tcp}
import akka.stream.scaladsl.Tcp.{IncomingConnection, ServerBinding}
import akka.util.ByteString
import mixer.HouseAccount
import mixer.Transactions.Transaction
import play.api.libs.json.{Format, JsArray, JsError, JsNumber, JsObject, JsPath, JsResult, JsString, JsSuccess, JsValue, Json, JsonValidationError, Reads}
import server.MainSupport.{ClientAddressesCmd, ClientTransactionCmd}
import service.{ClientRepo, MixingService}

import scala.concurrent.{ExecutionContext, Future}
import pureconfig.loadConfig
import pureconfig.generic.auto._

import scala.util.{Failure, Success, Try}



object MainSupport{

  implicit val clientAddressCmdFmt: Format[ClientAddressesCmd] = Json.format[ClientAddressesCmd]
  implicit val clientTransactionCmdFmt: Format[ClientTransactionCmd] = Json.format[ClientTransactionCmd]

  sealed trait Command
  case class ClientAddressesCmd(user: String, addresses: Seq[String]) extends Command
  case class ClientTransactionCmd(user: String, houseAddress: String, amount: BigDecimal ) extends Command

  implicit object commandReads extends Reads[Command] {
    def reads(json: JsValue): JsResult[Command] = json match {
      case JsObject(mapObj) =>

        val perhapsClientAddress: Option[ClientAddressesCmd] =
          for{
            JsString(user) <- mapObj.get("user")
            JsArray(addresses) <- mapObj.get("addresses")
          } yield{
            ClientAddressesCmd(user, addresses.map(_.toString()))
          }

        val perhapsClientTransaction: Option[ClientTransactionCmd] =
          for{
            JsString(user) <- mapObj.get("user")
            JsString(houseAddress) <- mapObj.get("houseAddress")
            JsNumber(amount) <- mapObj.get("amount")
          } yield{
            ClientTransactionCmd( user, houseAddress, amount)
          }

        perhapsClientAddress map{ JsSuccess(_) } getOrElse
          perhapsClientTransaction.map{ JsSuccess(_) }.getOrElse{
            JsError(Seq(JsPath() -> Seq(JsonValidationError(s"invalid json for command${json.toString()}"))))
          }

      case _ =>
        JsError(Seq(JsPath() -> Seq(JsonValidationError(s"Invalid command: $json"))))
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
      case Failure(err) =>
        Left(s"Bad Json Command ${err.getMessage}:")
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

  /*
  Config case class loaded by pureconfig.  Eases testing with case classes vs. passing typesafe config objects around.
   */
  case class JobcoinConfig(server: Server, houseSeedAmount: BigDecimal, maxMixingTimeSeconds: Int, mixingFeeFactor: Double, jobcoinPrecision: Int, mixingDelayMillis: Long)
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
    Tcp().bind(config.server.host, config.server.port)


  /**
    * I decided to go the routes of IO Akka Streams vs. a pure Play app
    * This stream listens to commands on the port sent (via telnet, eg)
    * Only two types of commands are understood:
    * 1. Send seq of public key addresses.  Reply with house address.
    * 2. Send amount with house address.  Reply after all mixes are done.
    */
  connections.runForeach { connection =>

    val process = Flow[ByteString]
      .via(Framing.delimiter(ByteString("\n"), maximumFrameLength = 256, allowTruncation = true))
      .map( _.utf8String )
      .map(MainSupport.cmd)
      .mapAsync(8){ cmd =>
          cmd match{

            case Right(clientAddresses: ClientAddressesCmd) =>
              Future{clientRepo.addAddress(clientAddresses.user, clientAddresses.addresses)}.map { ci =>
                s"Coins sent to ${ci.userHouseDepositAddress} will be mixed and sent to ${ci.userAddresses.mkString(",")}"
              }

            case Right(cmd: ClientTransactionCmd) =>
              mixingService.mix(Transaction(cmd.user, cmd.houseAddress, cmd.amount, Seq.empty, Seq.empty)).map{ maybeMixedTransactions =>
                maybeMixedTransactions match{
                  case Right(x: Transaction) =>
                    x.outputs
                      .map{
                        case( address, amt) =>
                          s"$amt sent to $address"
                      }.mkString(",")
                  case Left(err) => s"Failure to mix: $err"
                }

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
