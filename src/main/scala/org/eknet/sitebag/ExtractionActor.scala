package org.eknet.sitebag

import akka.actor.{Props, ActorLogging, Actor}
import org.eknet.sitebag.content.{Extractor, Content}

object ExtractionActor {
  def apply() = Props(classOf[ExtractionActor])
}
class ExtractionActor extends Actor with ActorLogging {

  val extractor = SitebagSettings(context.system).extractor

  def receive = {
    case c: Content =>
      sender ! Extractor.extract(c, extractor)
  }
}
