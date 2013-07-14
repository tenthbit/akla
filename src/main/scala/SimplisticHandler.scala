package net.tenthbit.server

import akka.actor.{ Actor, ActorRef, Props }
import akka.io.{ IO, Tcp }
import akka.util.ByteString

import net.liftweb.json._
import net.liftweb.json.JsonDSL._
import net.liftweb.json.Serialization.{ read, write }
import net.tenthbit.protocol._

class SimplisticHandler(server: ActorRef, connection: ActorRef) extends Actor {
  import Tcp._
  implicit val formats = Serialization.formats(NoTypeHints)

  def receive = {
    case payload: Payload => connection ! Write(ByteString(write(payload) + "\n", "utf-8"))
    case payload: String => connection ! Write(ByteString(write(payload) + "\n", "utf-8"))
    case e: ConnectionClosed => server ! Disconnection(self)
    case Received(data) => {
      val json = parse(data.decodeString("utf-8"))
      (json \ "op").extract[String] match {
        case "act" => server ! BroadcastString(write(json))
        case "auth" => {
          val username = (json \ "ex" \ "username") match {
            case JString(username) => Some(username)
            case _ => None
          }
          val password = (json \ "ex" \ "password") match {
            case JString(password) => Some(password)
            case _ => None
          }
          if (username == Some("relrod") && password == Some("mypassword")) {
            self ! (
              ("op" -> "ack") ~
              ("ex" ->
                ("for" -> "auth")))
          } else {
            self ! (
              ("op" -> "error") ~
              ("ex" ->
                ("for" -> "auth")))
          }
        }
        case otherwise => server ! Broadcast(Ack(Some(AckExtras(otherwise))))
      }
    }
    case PeerClosed => context stop self
  }
}
