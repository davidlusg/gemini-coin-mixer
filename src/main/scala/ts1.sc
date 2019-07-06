import play.api.libs.json.{Format, JsValue, Json, Reads}

implicit val clientAddressCmdFmt: Format[ClientAddresses] = Json.format[ClientAddresses]

object Command{
  def unapply(foo: Command): Option[(String, JsValue)] = {
    val (prod: Product, sub) = foo match {
      case b: ClientAddresses => (b, Json.toJson(b)(clientAddressCmdFmt))
    }
    Some(prod.productPrefix -> sub)
  }

  def apply(`class`: String, data: JsValue): Command = {
    (`class` match {
      case "ClientAddresses" => Json.fromJson[ClientAddresses](data)(clientAddressCmdFmt)
    }).get
  }
}

sealed trait Command
//  object ClientAddresses{
//    def apply(user: String, addresses: Seq[String]) =
//      new ClientAddresses(user, addresses)
//    def unapply(clientAddresses: ClientAddresses): Option[(String, Seq[String])] =
//      Some(clientAddresses.user, clientAddresses.addresses)
//  }
case class ClientAddresses( user: String, addresses: Seq[String] ) extends Command
implicit val commandFormats: Format[Command] = Json.format[Command]


Json.parse("""{"user": "user1", "addresses":["Ford"]}""").validate[Command]
val y = 1