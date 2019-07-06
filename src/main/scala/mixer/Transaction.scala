package mixer

import java.security.PublicKey

object Transaction {

  type PublicKey = String

  case class TransactionOutput(id: String, recipient: PublicKey, amount: BigDecimal, parentTransactionId: String )
  case class TransactionInput( transactionOutputId: String, UXTO: TransactionOutput )
  case class Transaction(clientPublicAddress: PublicKey, houseEphemeralAddress: PublicKey, amount: BigDecimal, inputs: Seq[TransactionInput], outputs: Seq[(PublicKey, BigDecimal)])

}
