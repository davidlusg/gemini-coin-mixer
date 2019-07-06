import java.security.PublicKey

class ClientRepo {

  def register( user: PublicKey,  addresses: Seq[PublicKey] ) = ???
  def addresses( user: PublicKey ): Seq[PublicKey] = ???

}
