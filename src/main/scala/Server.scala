package net.tenthbit.server

import akka.actor.{ Actor, ActorRef, ActorLogging, ActorSystem, Deploy, Props }
import akka.io.{ BackpressureBuffer, DelimiterFraming, IO, StringByteStringAdapter, Tcp, TcpPipelineHandler, TcpReadWriteAdapter, SslTlsSupport }
import akka.io.TcpPipelineHandler.{ Init, WithinActorContext }
import akka.pattern.ask
import akka.util.{ ByteString, Timeout }

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
  // https://github.com/cloudaloe/pipe/blob/master/src/main/scala/pipe/ssl.scala
  val context: SSLContext = SSLContext.getInstance("SSLv3")
  val keyStore = KeyStore.getInstance("JKS")
  val keyStoreFile = new FileInputStream("/home/ricky/devel/tenthbit/ssl/keystore.jks")
  val keyStorePassword = ""
  keyStore.load(keyStoreFile, keyStorePassword.toCharArray)
  keyStoreFile.close

  val keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm)
  val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm)
  keyManagerFactory.init(keyStore, keyStorePassword.toCharArray)
  trustManagerFactory.init(keyStore)
  context.init(keyManagerFactory.getKeyManagers, trustManagerFactory.getTrustManagers, null)

  def getEngine: SSLEngine = {
    val engine: SSLEngine = context.createSSLEngine
    engine setUseClientMode false
    engine setNeedClientAuth false
    engine
  }
}

case class Broadcast(obj: Payload)
case class BroadcastString(obj: String)
case class Disconnection(handler: ActorRef)

class Server extends Actor with ActorLogging {
  import Tcp._
  import context.system

  implicit val defaults = DefaultFormats
  implicit val timeout = Timeout(300.millis)
  val clientPipelines = scala.collection.mutable.ArrayBuffer[(Init[WithinActorContext, String, String], ActorRef)]()

  IO(Tcp) ! Bind(self, new InetSocketAddress("0.0.0.0", 10817))

  def receive: Receive = {
    case _: Bound => context.become(bound(sender))
  }

  def bound(listener: ActorRef): Receive = {
    case Broadcast(obj) => {
      clientPipelines.foreach(e => println(e))
      clientPipelines.foreach { case (init, pipeline) => pipeline ! init.Command(write(obj) + "\n") }
    }
    case Connected(remote, _) => {
      val init = TcpPipelineHandler.withLogger(
        log,
        new StringByteStringAdapter("utf-8") >>
          new DelimiterFraming(maxSize = 1024, delimiter = ByteString('\n'), includeDelimiter = true) >>
            new TcpReadWriteAdapter >>
              new SslTlsSupport(TenthbitSSL.getEngine) >>
                new BackpressureBuffer(lowBytes = 100, highBytes = 1000, maxBytes = 1000000))

      val connection = sender
      val handler = context.actorOf(Props(new SslHandler(self, init)).withDeploy(Deploy.local))
      val pipeline = context.actorOf(TcpPipelineHandler.props(init, connection, handler).withDeploy(Deploy.local))
      clientPipelines += ((init, pipeline))

      (connection ? Tcp.Register(pipeline)) onComplete { case _ =>
        val welcome = Welcome(
          Some(
            WelcomeExtras(
              "elrod.me",
              "scala-10b/1.0.0",
              List("password", "anonymous"),
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
