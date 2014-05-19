package org.eknet.sitebag.search

import akka.actor._
import akka.pattern.pipe
import org.eknet.sitebag._
import porter.model.Ident
import akka.actor.Terminated
import scala.Some

class SearchActor(dbname: Option[String]) extends Actor with ActorLogging {
  import context.dispatcher

  private val settings = SitebagSettings(context.system)
  private val mongo = dbname.map(settings.makeMongoClient) getOrElse settings.defaultMongoClient

  private var indexes = Map.empty[Ident, ActorRef]

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
      indexes = indexes filterNot { case (_, ar) => ar == ref }

    case rbi@ RebuildIndex(account, flag) =>
      account match {
        case Some(acc) =>
          accountIndex(acc) forward rbi
        case _         =>
          mongo.listAccounts.map(al => RebuildStart(sender, al.names, flag)) pipeTo rebuilder
          sender ! Success("Complete Index Rebuild started")
      }
    case rs@CheckRebuildStatus(account) =>
      accountIndex(account) forward rs

    case ReextractionDone(account) =>
      self forward RebuildIndex(Some(account), onlyIfEmpty = false)

    case se: SitebagEvent =>
      accountIndex(se.account) forward se

    case list: ListEntries =>
      accountIndex(list.account) forward list
  }

  private def accountIndex(account: Ident): ActorRef = {
    indexes.get(account) getOrElse {
      val ref = context.watch(context.actorOf(AccountSearchActor(account, mongo)))
      indexes = indexes.updated(account, ref)
      ref
    }
  }
  override def preStart() = {
    context.system.eventStream.subscribe(self, classOf[SitebagEvent])
    self ! RebuildIndex(None, onlyIfEmpty = true)
  }
}
object SearchActor {
  def apply(): Props = Props(classOf[SearchActor], None)
  def apply(dbname: String): Props = Props(classOf[SearchActor], Some(dbname))
}