package service

import akka.actor.{Actor, ActorRef, PoisonPill, Props}
import mixer.HouseAccount.Launder
import mixer.Transaction.{Transaction}

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits._
import akka.pattern.ask
import akka.util.Timeout
import akka.pattern.pipe
import server.Main.JobcoinConfig

object ClientActor{

  def apply( houseAccount: ActorRef, transaction: Transaction, config: JobcoinConfig ): Props = Props( new ClientActor( houseAccount, transaction, config ))

}

class ClientActor(houseAccount: ActorRef, transaction: Transaction, config: JobcoinConfig) extends Actor{

  implicit val timeout: Timeout = 4 seconds
  case class Tick(sender: ActorRef)

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


  def mix( transaction: Transaction ): Future[Transaction] = {
    val addresses: Seq[String] = clientRepo.getAddresses(transaction.houseEphemeralAddress)

    if(addresses.isEmpty) throw new Exception(s"no addresses for ${transaction.clientPublicAddress}")

    else{
      val clientDelegate = system.actorOf(ClientActor(houseAccount, transaction, config), transaction.clientPublicAddress)

      implicit val timeout: Timeout = 5 seconds

      (clientDelegate ?  Launder( transaction )).mapTo[Transaction]
    }
  }

}
