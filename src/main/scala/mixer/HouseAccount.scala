package mixer

import akka.actor.Actor
import mixer.HouseAccount.Launder
import mixer.Transaction.Transaction
import akka.pattern.ask
import akka.util.Timeout
import akka.pattern.pipe
import server.Main.JobcoinConfig
import service.ClientRepo

import scala.collection.mutable.ListBuffer
import scala.concurrent.Future

object HouseAccount{
  case class Launder( tx: Transaction )
}

class HouseAccount( clientRepo: ClientRepo, config: JobcoinConfig) extends Actor with HouseCalculator {

  import scala.concurrent.ExecutionContext.Implicits._

  var accountBalance: BigDecimal = config.houseSeedAmount

  def receive = {
    case Launder( tx: Transaction ) =>

      val fee = BigDecimal(0.0001) * tx.amount

      val input: (PublicKey, BigDecimal) = (tx.houseEphemeralAddress, tx.amount + fee)
      ledgerIn += input

      val cleanedOutput = (tx.clientPublicAddress, tx.amount - fee)
      ledgerOut += cleanedOutput

      val f = Future{
        val addresses = clientRepo.getAddresses( tx.houseEphemeralAddress )
        val allocated = addresses zip allocateToNAddresses( tx.amount, addresses.size )
        mixer.Transaction.Transaction(tx.clientPublicAddress, tx.houseEphemeralAddress, tx.amount - fee, Seq.empty, allocated)
      }

      f pipeTo( sender )

    case _ =>
  }
}
