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

class Room extends Actor with ActorLogging {

    import Tcp._

    implicit val defaults = DefaultFormats

    val clientRefs = scala.collection.mutable.ListBuffer[Client]()

    def receive = {
      case Room.Join(client) =>
        clientRefs += client
      //case Leave(client) => clientRefs -= client
      case BroadcastString(obj) => {
        clientRefs.foreach {
          case Client(handerl, pipeline, init) => pipeline ! init.Command(obj + "\n")
        }
      }
    }
}

object Room {
  case class Join(client: Client)
}
