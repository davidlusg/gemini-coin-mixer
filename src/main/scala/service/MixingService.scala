package service

import akka.actor.{Actor, ActorRef, PoisonPill, Props}
import mixer.HouseAccount.Launder
import mixer.Transaction.{PublicKey, Transaction}

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits._
import akka.pattern.ask
import akka.util.Timeout
import akka.pattern.pipe
import mixer.HouseCalculator
import server.Main.JobcoinConfig

object ClientActor{

  def apply( houseAccount: ActorRef, transaction: Transaction, config: JobcoinConfig ): Props = Props( new ClientActor( houseAccount, transaction, config ))

}

class ClientActor(houseAccount: ActorRef, transaction: Transaction, jobConfig: JobcoinConfig) extends Actor with HouseCalculator{

  implicit val timeout: Timeout = 4 seconds
  case class Tick(sender: ActorRef)

  override def config = jobConfig

  def process( toBeMixed: Seq[(BigDecimal, PublicKey)] = Seq.empty ): Receive = {
    case Launder( transaction ) =>
      context.system.scheduler.scheduleOnce(50 milliseconds, self, Tick(sender()))

    case Tick(sender) =>
      toBeMixed match{
        case h::t =>
          context.become( process(t) )
          (houseAccount ? Launder( transaction))
            .pipeTo(sender)
          .andThen{
            case _ => self ! Tick(sender)
          }
        case _ =>
          self ! PoisonPill
      }


  }

  def receive = {
    case Launder( transaction ) =>
      context.system.scheduler.scheduleOnce(50 milliseconds, self, Tick(sender()))

    case Tick(sender) =>
      (houseAccount ? Launder( transaction))
        .pipeTo(sender)
        .andThen{
          case _ =>
            println( s"Finished my work - going away")
            self ! PoisonPill
        }
  }
}

class MixingService( clientRepo: ClientRepo, houseAccount: ActorRef, config: JobcoinConfig ) {

  import server.Main.system

  import scala.concurrent.ExecutionContext.Implicits._


  def mix( transaction: Transaction ): Future[Either[String,Transaction]] = {
    val addresses: Seq[String] = clientRepo.getAddresses(transaction.houseEphemeralAddress)

    if(addresses.isEmpty) Future{Left(s"no addresses for ${transaction.houseEphemeralAddress} - NOT a valid House Address")}

    else{
      val clientDelegate = system.actorOf(ClientActor(houseAccount, transaction, config), transaction.clientPublicAddress)

      implicit val timeout: Timeout = 5 seconds

      (clientDelegate ?  Launder( transaction )).mapTo[Transaction].map{Right(_)}
    }
  }

}
