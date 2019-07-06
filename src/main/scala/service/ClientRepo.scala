package service

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

import service.ClientRepo.ClientInfo

object ClientRepo{
  case class ClientInfo(
                       user: String,
                       userHouseDepositAddress: String,
                       userAddresses: Seq[String]
                       )
}


class ClientRepo {

  // k,v = input address, output addresses
  private val repo: java.util.concurrent.ConcurrentMap[String, ClientInfo] = new ConcurrentHashMap()

  def addAddress(user: String, addresses: Seq[String]) = {
    val houseAddress = UUID.randomUUID().toString
    val added = ClientInfo(user, houseAddress, addresses)
    repo.put(houseAddress, added)
    added
  }

  def getAddresses(houseDepositAddress: String): Seq[String] = {
    Option(repo.get(houseDepositAddress)) map{ ci =>
      ci.userAddresses
    } getOrElse Seq.empty[String]
  }

}
