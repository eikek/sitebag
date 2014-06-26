package org.eknet.sitebag.search

import akka.actor._
import akka.pattern.pipe
import org.eknet.sitebag._
import porter.model.Ident
import akka.actor.Terminated
import scala.Some
import org.eknet.sitebag.mongo.SitebagMongo
import java.util.concurrent.atomic.AtomicReference

class SearchActor(mongo: SitebagMongo) extends Actor with ActorLogging {
  import context.dispatcher

  private val indexes = new AtomicReference(Map.empty[Ident, ActorRef])

  private val rebuilder = context.actorOf(Props(new Actor {
    def receive = idle

    def idle: Receive = {
      case RebuildStart(client, names, ifEmpty) =>
        context.become(waiting(client, names.size))
        names.foreach(acc => accountIndex(acc) ! RebuildIndex(Some(acc), ifEmpty))
    }
    def waiting(client: ActorRef, left: Int): Receive = {
      case RebuildStart(other, _, _) =>
        other ! Failure("Index rebuilding is currently in progress.")

      case r: Result[_] if left > 1 =>
        context.become(waiting(client, left -1))

      case r: Result[_] if left == 1 =>
        log.info("All indexes have been rebuild.")
        client ! Success("All indexes have been rebuild.")
        context.become(idle)
    }
  }))
  case class RebuildStart(client: ActorRef, names: List[Ident], ifEmpty: Boolean)

  def receive = {
    case Terminated(ref) =>
      val next = indexes.get() filterNot { case (_, ar) => ar == ref }
      indexes.set(next)

    case rbi@ RebuildIndex(account, flag) =>
      account match {
        case Some(acc) =>
          accountIndex(acc) forward rbi
        case _         =>
          val client = sender
          mongo.listAccounts.map(al => RebuildStart(client, al.names, flag)) pipeTo rebuilder
          client ! Success("Complete Index Rebuild started")
      }
    case rs@CheckRebuildStatus(account) =>
      accountIndex(account) forward rs

    case ReextractionDone(account) =>
      self ! RebuildIndex(Some(account), onlyIfEmpty = false)

    case se: SitebagEvent =>
      accountIndex(se.account) ! se

    case list: ListEntries =>
      accountIndex(list.account) forward list
  }

  @scala.annotation.tailrec
  private final def accountIndex(account: Ident): ActorRef = {
    val map = indexes.get()
    val (ref, nextMap) = map.get(account).map(ref => ref → map) getOrElse {
      val ref = context.watch(context.actorOf(AccountSearchActor(account, mongo)))
      ref → map.updated(account, ref)
    }
    if ((map eq nextMap) || indexes.compareAndSet(map, nextMap)) {
      ref
    } else {
      accountIndex(account)
    }
  }

  override def preStart() = {
    context.system.eventStream.subscribe(self, classOf[SitebagEvent])
    self ! RebuildIndex(None, onlyIfEmpty = true)
  }
}
object SearchActor {
  def apply(mongo: SitebagMongo): Props = Props(classOf[SearchActor], mongo)
}
