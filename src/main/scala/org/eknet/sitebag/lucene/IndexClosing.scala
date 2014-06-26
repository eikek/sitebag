package org.eknet.sitebag.lucene

import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import akka.actor.{Actor, ActorLogging}

private[lucene] trait IndexClosing {
  _: Actor with ActorLogging ⇒

  def shuttingdown(refCounter: AtomicInteger, started: Long = System.currentTimeMillis)(implicit ec: ExecutionContext): Receive = {
    case IndexClosing.Check  ⇒
      if (refCounter.get() > 0) {
        val waited = System.currentTimeMillis - started
        if (waited > 2000) {
          log.warning("Forcing stopping of IndexWriterActor despite running writers")
          context.stop(self)
        } else {
          //wait a bit and try again
          context.system.scheduler.scheduleOnce(50.milliseconds, self, IndexClosing.Check)
        }
      } else {
        context.stop(self)
      }
  }

}

object IndexClosing {

  case object Check

}
