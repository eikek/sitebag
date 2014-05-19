package org.eknet.sitebag.search

import java.nio.file.Files
import java.nio.file.Path
import scala.concurrent.duration._
import scala.concurrent.Future
import akka.actor._
import akka.pattern.pipe
import akka.pattern.ask
import akka.util.Timeout
import porter.model.Ident
import org.eknet.sitebag._
import org.eknet.sitebag.lucene._
import org.eknet.sitebag.model._
import org.eknet.sitebag.mongo.SitebagMongo
import org.eknet.sitebag.search.AccountSearchActor.ListQuery

/**
 * Maintains the index for an account. Each account has its own index and all of them
 * are managed via its parent, which is [[SearchActor]]. The parent will create the
 * actors on demand and re-create them if they die. So by default, this actor is stopped
 * on error.
 *
 * @param account
 * @param mongo
 */
class AccountSearchActor(account: Ident, mongo: SitebagMongo) extends Actor with ActorLogging {
  import context.dispatcher
  private implicit val timeout: Timeout = 5.seconds

  private val settings = SitebagSettings(context.system)
  private val indexDir = makeIndexDir(account)
  private val indexActor = context.actorOf(IndexActor(indexDir))

  protected def makeIndexDir(account: Ident): Path = {
    settings.indexDir.resolve(account.name)
  }

  private val rebuilder = context.actorOf(Props(new Actor {
    def receive = idle

    def idle: Receive = {
      case RebuildStart =>
        log.info(s"Index for '${account.name}' is being rebuild...")
        context.become(rebuilding(sender, 0, 0, System.currentTimeMillis()))
        indexActor ! ClearIndex
        val f = mongo.withPageEntries(account) { entry =>
          indexActor ! Mod(_.updateDocument(entry), s"Add entry ${entry.id} to index for ${account.name}")
        }
        f.map(RebuildsSent.apply) pipeTo self

      case CheckRebuildStatus(_) =>
        sender ! Success(RebuildIdle, "Index rebuild done.")
    }
    def rebuilding(client: ActorRef, responses: Int = 0, sent: Int, started: Long): Receive = {
      case RebuildStart =>
        sender ! Failure("Index is being currently being rebuild.")

      case CheckRebuildStatus(_) =>
        sender ! Success(RebuildRunning(responses, started), "Index is currently being rebuild.")

      case RebuildsSent(all) =>
        if (responses == all) done(client, all, System.currentTimeMillis() - started)
        else context.become(rebuilding(client, responses, all, started))

      case Success(_, message) =>
        if (message.nonEmpty) {
          log.debug(message)
        }
        waitForNext(client, responses + 1, sent, started)

      case Failure(msg, error) =>
        error.map(e => log.error(e, msg)).getOrElse(log.error(msg))
        waitForNext(client, responses +1, sent, started)
    }

    def done(client: ActorRef, all: Int, time: Long) {
      val msg = s"Index rebuild done for '${account.name}'. Pushed $all entries to index in $time ms."
      log.info(msg)
      client ! Success(msg)
      context.become(idle)
    }
    def waitForNext(client: ActorRef, responses: Int, sent: Int, started: Long) {
      if (responses == sent) done(client, sent, System.currentTimeMillis() - started)
      else context.become(rebuilding(client, responses, sent, started))
    }
  }))

  private case object RebuildStart
  private case class RebuildsSent(count: Int)

  def receive = {
    case EntrySaved(acc, FullPageEntry(entry, _)) =>
      indexActor ! Mod(_.updateDocument(entry), s"Added entry '${acc.name}:${entry.url}' to index")

    case EntryDropped(acc, id) =>
      indexActor ! Mod(_.deleteDocument("_id" -> id), s"Deleted entry '${acc.name}:$id' from index")

    case event: SitebagEntryEvent =>
      //all other events: load page entry and update index
      val f = mongo.getEntry(event.account, event.entryId)
      f onSuccess {
        case Success(Some(entry), _) =>
          indexActor ! Mod(_.updateDocument(entry), s"Added entry '${event.account.name}:${entry.url}' to index")
      }

    case RebuildIndex(_, ifEmpty) =>
      if (Files.exists(indexDir) && ifEmpty) {
        sender ! Success("Don't rebuild index, since it already exists.")
      } else {
        rebuilder forward RebuildStart
      }
    case rs: CheckRebuildStatus =>
      rebuilder forward rs

    case ListEntries(_, tags, archived, query, page, complete) =>
      if (indexDir.notExistsOrEmpty) sender ! Success(Nil)
      else {
        val qm = new ListQuery(query, tags, archived)
        val f = (indexActor ? QueryDirectory.create[PageEntry](qm, page)).mapTo[Result[Iterable[PageEntry]]]
        val result = if (complete) {
          f.flatMap {
            case Success(list, _) => Future.sequence(list.getOrElse(Nil).toList.map(e => mongo.getEntry(account, e.id)))
            case f: Failure => Future.successful(f)
          }
        } else {
          f.map(_.mapmap(_.toList))
        }
        result pipeTo sender
      }

    case Success(_, message) =>
      if (message.nonEmpty) {
        log.debug(message)
      }

    case Failure(msg, error) =>
      error.map(e => log.error(e, msg)).getOrElse(log.error(msg))
  }

  override def supervisorStrategy = SupervisorStrategy.stoppingStrategy
}
object AccountSearchActor {
  def apply(account: Ident, mongo: SitebagMongo): Props = Props(classOf[AccountSearchActor], account, mongo)

  private class ListQuery(query: String, tags: Set[Tag], archived: Option[Boolean]) extends QueryMaker {
    def apply() = {
      val qb = new StringBuilder(query)
      if (!query.contains("tag:") && tags.nonEmpty) {
        qb.append(tags.map(t => s"tag:${t.name}").mkString(" AND ", " AND ", ""))
      }
      if (!query.contains("archived:")) {
        archived.map(a => qb.append(" AND ").append(s"archived:$a"))
      }
      QueryMaker.fromString(qb.toString(), "content").apply()
    }
  }
}