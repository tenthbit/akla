package net.tenthbit.server

import akka.actor.{ Actor, ActorRef, ActorLogging, ActorSystem, Deploy, Props }
import akka.io.{ BackpressureBuffer, DelimiterFraming, IO, StringByteStringAdapter, Tcp, TcpPipelineHandler, TcpReadWriteAdapter, SslTlsSupport }
import akka.io.TcpPipelineHandler.{ Init, WithinActorContext }
import akka.pattern.ask
import akka.util.{ ByteString, Timeout }

import com.typesafe.config._

import net.liftweb.json._
import net.liftweb.json.JsonDSL._
import net.liftweb.json.Serialization.{ read, write }
import net.tenthbit.protocol._

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

import java.io.FileInputStream
import java.net.InetSocketAddress
import java.security.KeyStore

import javax.net.ssl.{ SSLContext, SSLEngine, TrustManagerFactory, KeyManagerFactory }

object TenthbitSSL {
  def apply(keyStorePath: String, keyStorePassword: String) = {
    // https://github.com/cloudaloe/pipe/blob/master/src/main/scala/pipe/ssl.scala
    val context: SSLContext = SSLContext.getInstance("SSLv3")
    val keyStore = KeyStore.getInstance("JKS")
    val keyStoreFile = new FileInputStream(keyStorePath)
    keyStore.load(keyStoreFile, keyStorePassword.toCharArray)
    keyStoreFile.close

    val keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm)
    val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm)
    keyManagerFactory.init(keyStore, keyStorePassword.toCharArray)
    trustManagerFactory.init(keyStore)
    context.init(keyManagerFactory.getKeyManagers, trustManagerFactory.getTrustManagers, null)

    val engine: SSLEngine = context.createSSLEngine
    engine setUseClientMode false
    engine setNeedClientAuth false
    engine
  }
}

case class Broadcast(obj: Payload)
case class BroadcastString(obj: String)
case class BroadcastChannelString(name: String, obj: String)
case class Client(handler: ActorRef, pipeline: ActorRef, init: Init[WithinActorContext, String, String])
case class Disconnection(handler: ActorRef)
case class Join(name: String)

class Server extends Actor with ActorLogging {
  val conf = ConfigFactory.load()

  import Tcp._
  import context.system

  implicit val defaults = DefaultFormats
  implicit val timeout = Timeout(300.millis)
  val clientPipelines = scala.collection.mutable.ArrayBuffer[Client]()

  IO(Tcp) ! Bind(self, new InetSocketAddress(conf.getString("server.bind"), conf.getInt("server.port")))

  // TODO: This will end up being mutable, but that's fine here.
  val rooms: Map[String, ActorRef] = Map(
    "firstchannel" -> context.actorOf(Props(new Room))
  )

  def receive: Receive = {
    case _: Bound => context.become(bound(sender))
  }

  def bound(listener: ActorRef): Receive = {
    case Broadcast(obj) =>
      clientPipelines.foreach { case Client(handler, pipeline, init) => pipeline ! init.Command(write(obj) + "\n") }
    case BroadcastChannelString(name, obj) => {
      val room = rooms.get(name)
      if (room.isDefined) {
        room.get ! BroadcastString(obj)
        sender ! true
      } else {
        sender ! false
      }
    }
    case Join(name) => {
      clientPipelines.find(_.handler == sender).map { client =>
        rooms.get(name).map(_ ! Room.Join(client))
      }
    }
    case Connected(remote, _) => {
      val sslEngine = TenthbitSSL(
        conf.getString("server.ssl.keystore"),
        conf.getString("server.ssl.password"))

      val init = TcpPipelineHandler.withLogger(
        log,
        new StringByteStringAdapter("utf-8") >>
          new DelimiterFraming(maxSize = 1024, delimiter = ByteString('\n'), includeDelimiter = true) >>
            new TcpReadWriteAdapter >>
              new SslTlsSupport(sslEngine) >>
                new BackpressureBuffer(lowBytes = 100, highBytes = 1000, maxBytes = 1000000))

      val connection = sender
      val handler = context.actorOf(Props(new SslHandler(self, init)).withDeploy(Deploy.local))
      val pipeline = context.actorOf(TcpPipelineHandler.props(init, connection, handler).withDeploy(Deploy.local))
      clientPipelines += Client(handler, pipeline, init)

      (connection ? Tcp.Register(pipeline)) onComplete { case _ =>
        val welcome = Welcome(
          Some(
            WelcomeExtras(
              conf.getString("server.host"),
              "akla/1.0.0",
              List("password"),
              System.currentTimeMillis)))

        pipeline ! init.Command(write(welcome) + "\n")
      }
    }
  }
}

object Server extends App {
  val system = ActorSystem("ScalaTenthbitSystem")
  system.actorOf(Props(new Server))
}
