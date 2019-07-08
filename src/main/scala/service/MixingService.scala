package service

import akka.actor.{Actor, ActorRef, PoisonPill, Props}
import mixer.HouseAccount.Launder
import mixer.Transactions.{PublicKey, Transaction}

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits._
import akka.pattern.ask
import akka.util.Timeout
import akka.pattern.pipe
import mixer.HouseCalculator
import server.Main.JobcoinConfig

object ClientActor{

  def apply( houseAccount: ActorRef, clientRepo: ClientRepo, transaction: Transaction, config: JobcoinConfig ): Props = Props( new ClientActor( houseAccount, clientRepo, transaction, config ))

}

/**
  * The ClientActor does most of the heavy lifting for mixing a single transaction into multiple transactions.
  * The actor is an ephemeral actor - it holds the state of the transactions and delays laundering each transaction until all are done.
  * Then it goes away.
  * @param houseAccount
  * @param clientRepo
  * @param transaction
  * @param jobConfig
  */
class ClientActor(houseAccount: ActorRef, clientRepo: ClientRepo, transaction: Transaction, jobConfig: JobcoinConfig) extends Actor with HouseCalculator{

  implicit val timeout: Timeout = 4 seconds
  case class Tick(sender: ActorRef)

  override def config = jobConfig

  def publish( pair: (PublicKey, BigDecimal) ) : Future[Unit] = Future{ println( s"Sending transaction ${pair._2} ${pair._1}  to P2P")}

  def process( orig: List[(PublicKey, BigDecimal)] = List.empty[(PublicKey, BigDecimal)], toBeMixed: List[(PublicKey, BigDecimal)] = List.empty[(PublicKey, BigDecimal)] ): Receive = {
    case Launder( transaction ) =>
      val toAddresses: List[String] = clientRepo.getAddresses(transaction.houseEphemeralAddress).toList
      val amounts: List[BigDecimal] = allocateToNAddresses(transaction.amount, toAddresses.size)
      val addresses_amounts: List[(PublicKey, BigDecimal)] = toAddresses zip amounts
      context.become( process( addresses_amounts, addresses_amounts) )
      context.system.scheduler.scheduleOnce(jobConfig.mixingDelayMillis milliseconds, self, Tick(sender()))

    case Tick(sender) =>
      toBeMixed match {

        case h::t =>
          context.become( process(orig, t) )
          (houseAccount ? Launder( transaction))
            .mapTo[Transaction]
            .flatMap{ _ =>
              publish(h)
            }
          .andThen{ case _ =>
            context.system.scheduler.scheduleOnce(jobConfig.mixingDelayMillis milliseconds, self, Tick(sender))
          }

        case Nil =>
          Future{transaction.copy(outputs = orig)}
            .pipeTo(sender)
              .andThen{ case _ =>
                println( s"Finished my work - going away")
                self ! PoisonPill
              }

        case err =>
          println( s"GotBad $err")

      }

  }

  def receive: Receive = process()

}

class MixingService( clientRepo: ClientRepo, houseAccount: ActorRef, config: JobcoinConfig ) {

  import server.Main.system

  import scala.concurrent.ExecutionContext.Implicits._


  def mix( transaction: Transaction ): Future[Either[String,Transaction]] = {
    val addresses: Seq[String] = clientRepo.getAddresses(transaction.houseEphemeralAddress)

    if(addresses.isEmpty) Future{Left(s"no addresses for ${transaction.houseEphemeralAddress} - NOT a valid House Address")}

    else{
      val clientDelegate = system.actorOf(ClientActor(houseAccount, clientRepo, transaction, config), transaction.clientPublicAddress)

      implicit val timeout: Timeout = 5 seconds

      (clientDelegate ?  Launder( transaction ))
        .mapTo[Transaction]
        .map{
          case tx =>
            Right(tx)
        }
    }
  }

}
