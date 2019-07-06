package mixer

import org.scalatest.{Matchers, WordSpec}
import server.Main.{JobcoinConfig, Server}

import scala.collection.mutable.ListBuffer

class HouseCalculatorSpec extends WordSpec with Matchers {

  "House Calculator" when {

    val jobcoinPrecision = 8
    val calc = new HouseCalculator {
      override def config = JobcoinConfig(Server("localhost", 9000), BigDecimal(1.0), 10, 0.01d, jobcoinPrecision)
    }

    "allocating 1 Jobcoin to 4 addresses" should {
      "allocate 0.25 to each one" in {
        val allocations = calc.allocateToNAddresses(BigDecimal(1), 4)

        allocations.size should be(4)
        allocations.filter {
          _ == BigDecimal(0.25)
        }.size should be(4)
        allocations.sum should be(1)
      }

    }

    "allocating 1 Jobcoin to 3 addresses" should {
      val allocations = calc.allocateToNAddresses(BigDecimal(1), 3)
      "allocate 0.33, 0.33 and 0.34" in {
        allocations.size should be(3)
        allocations.filter {
          _ == BigDecimal(0.33333333)
        }.size should be(2)
        allocations.sum should be(1)
      }

    }

    "allocating 0.99 Jobcoin to 3 addresses" should {
      val allocations = calc.allocateToNAddresses(BigDecimal(0.99), 3)
      "allocate 0.33, 0.33 and 0.33" in {
        allocations.size should be(3)
        allocations.filter {
          _ == BigDecimal(0.33000000)
        }.size should be(3)
        allocations.sum should be(0.99)
      }
    }

    "allocating 5 Jobcoins to 3 addresses" should {
      val allocations = calc.allocateToNAddresses(BigDecimal(5), 3)
      "allocate 2, 1 and 1" in {
        allocations.size should be(3)
        allocations.filter {
          _ == BigDecimal(1.66666667)
        }.size should be(2)
        allocations.sum should be(5)
      }
    }

    "mixing coins" should {
      "track the house balance" in {
        calc.ledgerIn = ListBuffer(
          ("clientAddress1", BigDecimal(1.0)) ,
          ("clientAddress1", BigDecimal(4.01))
        )

        calc.ledgerOut = ListBuffer(
          ("clientAddress1", BigDecimal(1.0)) ,
          ("clientAddress1", BigDecimal(4.0))
        )

        calc.balance should be (BigDecimal(0.01))
      }
    }
  }
}
