package net.tenthbit.server

import akka.actor.{ Actor, ActorRef, ActorSystem, Props }
import akka.io.{ IO, Tcp }
import akka.util.ByteString

import net.liftweb.json._
import net.liftweb.json.JsonDSL._
import net.tenthbit.protocol._

import java.net.InetSocketAddress

case class Broadcast(obj: Payload)
case class BroadcastString(obj: String)
case class Disconnection(handler: ActorRef)

class Server extends Actor {
  import Tcp._
  import context.system

  val clientHandlers = scala.collection.mutable.ArrayBuffer[ActorRef]()

  IO(Tcp) ! Bind(self, new InetSocketAddress("localhost", 10817))

  def receive = {
    case Broadcast(obj) => clientHandlers.foreach(_ ! obj)
    case BroadcastString(obj) => clientHandlers.foreach(_ ! obj)
    case Disconnection(handler) => clientHandlers -= handler
    case b @ Bound(localAddress) => println("Hello: " + localAddress)
    case CommandFailed(_: Bind) => context stop self
    case c @ Connected(remote, local) => {
      val handler = context.actorOf(Props(classOf[SimplisticHandler], self, sender))
      clientHandlers += handler

      val connection = sender
      connection ! Register(handler)

      val welcome = Welcome(
        Some(
          WelcomeExtras(
            "elrod.me",
            "scala-10b/1.0.0",
            List("password", "anonymous"),
            System.currentTimeMillis)))

      handler ! welcome
    }

  }
}

object Server extends App {
  val system = ActorSystem("ScalaTenthbitSystem")
  system.actorOf(Props(new Server))
}
