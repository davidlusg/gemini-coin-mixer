package server

object Test1 extends App{

  val m = new java.util.concurrent.ConcurrentHashMap[String, String]()

  m.putIfAbsent("a", "b")

  Option(m.get("b"))
}
