package org.eknet.sitebag

import akka.actor.{Props, Terminated, Actor, ActorLogging}
import spray.can.Http

class SitebagHttpHandler extends Actor with ActorLogging {
  var connections = 0

  val service = context.watch(context.actorOf(SitebagService(), "service"))

  def receive = {
    case Http.Bound(addr) =>
      log.info("Bound http interface to "+ addr)

    case Http.Connected(_, _) =>
      connections += 1
      sender ! Http.Register(service)

    case Terminated(ref) =>
      connections -= 1
      log.debug(s"Actor $ref terminated. Connections left: $connections")

  }
}
object SitebagHttpHandler {
  def apply() = Props(classOf[SitebagHttpHandler])
}
