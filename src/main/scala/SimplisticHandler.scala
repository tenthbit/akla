package net.tenthbit.server

import akka.actor.{ Actor, ActorRef, Props }
import akka.io.{ IO, Tcp }
import akka.util.ByteString

import net.liftweb.json._
import net.liftweb.json.Serialization.{ read, write }
import net.tenthbit.protocol._

class SimplisticHandler(connection: ActorRef) extends Actor {
  import Tcp._
  implicit val formats = Serialization.formats(NoTypeHints)
  def receive = {
    case payload: Payload => connection ! Write(ByteString(write(payload) + "\n", "utf-8"))
    case Received(data) => connection ! Write(data)
    case PeerClosed => context stop self
  }
}
