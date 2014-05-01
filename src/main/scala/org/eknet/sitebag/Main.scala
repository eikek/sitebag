package org.eknet.sitebag

import akka.actor._
import scala.util.control.NonFatal
import akka.io
import spray.can.Http
import java.net.InetSocketAddress
import scala.collection.immutable
import akka.io.Inet
import spray.can.server.ServerSettings
import spray.io.ServerSSLEngineProvider
import org.eknet.sitebag.MainActor.SitebagBind

object Main {
  val name = "sitebag"

  def main(args: Array[String]): Unit = {
    if (args.length != 0) {
      println("This app takes no arguments. They are ignored.")
    }
    val system = ActorSystem(name)
    val settings = SitebagSettings(system)
    try {
      val app = system.actorOf(MainActor(), name)
      system.actorOf(Props(classOf[Terminator], app), name+"-terminator")
      app ! SitebagBind(settings.bindHost, settings.bindPort)
    } catch {
      case NonFatal(e) ⇒ system.shutdown(); throw e
    }
  }

  class Terminator(app: ActorRef) extends Actor with ActorLogging {
    context watch app
    def receive = {
      case Terminated(_) ⇒
        log.info("application supervisor has terminated, shutting down")
        context.system.shutdown()
    }
  }
}

class MainActor extends Actor with ActorLogging {
  val listener = context.actorOf(SitebagHttpHandler(), "http-listener")

  implicit val s = context.system

  def receive = {
    case Http.Bound(address) =>
      log.info(s"Sitebag http service available at $address")

    case Http.CommandFailed(cmd) =>
      log.error(s"Command failed: $cmd")

    case SitebagBind(ep, bl, opts, settings) =>
      io.IO(Http) ! Http.Bind(listener, ep, bl, opts, settings)

    case unbind: Http.Unbind =>
      io.IO(Http) ! unbind

    case Http.Unbound =>
      log.info("Sitebag http service stopped.")
  }
}
object MainActor {
  def apply() = Props(classOf[MainActor])

  case class SitebagBind(endpoint: InetSocketAddress,
                         backlog: Int,
                         options: immutable.Traversable[Inet.SocketOption],
                         settings: Option[ServerSettings])(implicit val sslEngineProvider: ServerSSLEngineProvider)
  object SitebagBind {
    def apply(interface: String, port: Int = 80, backlog: Int = 100,
              options: immutable.Traversable[Inet.SocketOption] = Nil, settings: Option[ServerSettings] = None)(implicit sslEngineProvider: ServerSSLEngineProvider): SitebagBind =
      apply(new InetSocketAddress(interface, port), backlog, options, settings)
  }
}
