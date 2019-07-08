package service

import akka.actor.ActorSystem

import scala.concurrent.duration._
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import akka.pattern.ask
import akka.util.Timeout
import mixer.HouseAccount.Launder
import mixer.Transactions.Transaction
import org.scalatest._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import org.mockito.Mockito._
import org.mockito.ArgumentMatchers._
import server.Main.{JobcoinConfig, Server}

object MixingServiceSpec{
  val transaction = Transaction( clientPublicAddress = "user1", houseEphemeralAddress = "house1", amount = BigDecimal(10), inputs = Seq.empty, outputs = Seq.empty)

  val config = JobcoinConfig( server = Server("localhost", 8888),
    houseSeedAmount = BigDecimal(1000),
    maxMixingTimeSeconds = 60,
    mixingFeeFactor = 0.01,
    jobcoinPrecision = 8,
    mixingDelayMillis = 1000
  )
}

class MixingServiceSpec extends TestKit(ActorSystem("MixingServiceSpec")) with ImplicitSender with AsyncWordSpecLike with Matchers with MockitoSugar {

  import MixingServiceSpec._

  "MixingService" when {
    val houseAccount = TestProbe()
    val clientRepo: ClientRepo = mock[ClientRepo]
    when(
      clientRepo.getAddresses(any[String])
    ) thenReturn Seq("address1", "address2")

    "creating a ClientActor" should {

      val clientActor = system.actorOf(ClientActor( houseAccount.ref, clientRepo, transaction, config))

      "call the HouseAccount" in {
        clientActor ! Launder( transaction )
        houseAccount.expectMsgPF(5 seconds){
          case Launder(_) => assert(true)
          case err => fail( s"got unexpected msg $err")
        }
      }

    }
  }
}
