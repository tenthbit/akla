package net.tenthbit.server

import akka.actor.{ Actor, ActorRef, ActorSystem, Props }
import akka.io.{ IO, Tcp }
import akka.util.ByteString

import net.tenthbit.protocol._

import java.net.InetSocketAddress

class Server extends Actor {
  import Tcp._
  import context.system

  IO(Tcp) ! Bind(self, new InetSocketAddress("localhost", 10817))

  def receive = {
    case b @ Bound(localAddress) => println("Hello: " + localAddress)
    case CommandFailed(_: Bind) => context stop self
    case c @ Connected(remote, local) => {
      val handler = context.actorOf(Props(classOf[SimplisticHandler], sender))
      val connection = sender
      connection ! Register(handler)

      val welcome = Welcome(
        Some(
          Map(
            "server" -> "elrod.me",
            "software" -> "10bit Scala, 1.0.0",
            "now" -> System.currentTimeMillis,
            "auth" -> List("password", "anonymous"))))
      handler ! welcome
    }
  }
}

object Server extends App {
  val system = ActorSystem("ScalaTenthbitSystem")
   system.actorOf(Props(new Server))
}
