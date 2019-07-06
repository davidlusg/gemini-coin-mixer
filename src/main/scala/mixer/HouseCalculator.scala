package mixer

import server.Main.JobcoinConfig

import scala.collection.mutable.ListBuffer

trait HouseCalculator {

  type PublicKey = String

  def config: JobcoinConfig

  var ledgerIn: ListBuffer[(PublicKey, BigDecimal)] = ListBuffer.empty[(PublicKey, BigDecimal)]
  var ledgerOut: ListBuffer[(PublicKey, BigDecimal)] = ListBuffer.empty[(PublicKey, BigDecimal)]

  // 8 decimals
  def allocateToNAddresses(amt: BigDecimal, numAddresses: Int ) = {

    val approxPerAddress: BigDecimal = (amt/numAddresses).setScale(config.jobcoinPrecision, BigDecimal.RoundingMode.HALF_UP)

    List.tabulate[BigDecimal](numAddresses){ idx: Int =>
      if( idx < numAddresses-1) approxPerAddress
      else amt - (idx * approxPerAddress).setScale(8, BigDecimal.RoundingMode.HALF_UP)
    }
  }

  def balance = ledgerIn.map{ case(_, amt) => amt}.sum - ledgerOut.map{case(_, amt) => amt}.sum

}
