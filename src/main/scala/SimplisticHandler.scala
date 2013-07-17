package net.tenthbit.server

import akka.actor.{ Actor, ActorLogging, ActorRef, Props }
import akka.io.{ IO, Tcp }
import akka.io.TcpPipelineHandler.{ Init, WithinActorContext }
import akka.util.ByteString

import net.liftweb.json._
import net.liftweb.json.Extraction.decompose
import net.liftweb.json.JsonDSL._
import net.liftweb.json.Serialization.{ read, write }
import net.tenthbit.protocol._

import java.util.UUID

class SslHandler(server: ActorRef, init: Init[WithinActorContext, String, String])
  extends Actor with ActorLogging {

    import Tcp._

    implicit val defaults = DefaultFormats

    private def renderJValue(a: JValue): init.Command =
      init.Command(write(a) + "\n")

    private def addAck(a: JValue): JValue =
      a merge decompose(Map("ex" -> decompose(Map("isack" -> true))))

    private def addTimestamp(a: JValue): JValue =
      a merge decompose(Map("ts" ->  System.currentTimeMillis))

    private def addID(a: JValue): JValue =
      a merge decompose(Map("id" ->  UUID.randomUUID.toString))

    def receive = {
      // TODO: Actually do something with this.
      case e: ConnectionClosed => server ! Disconnection(self)
      case init.Event(data) => {
        val json = parse(data)
        (json \ "op").extract[String] match {
          case otherwise => sender ! renderJValue((addAck _ compose addTimestamp _ compose addID _)(json))
        }
      }
    }
}
