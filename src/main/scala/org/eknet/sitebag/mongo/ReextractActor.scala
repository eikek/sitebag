package org.eknet.sitebag.mongo

import scala.concurrent.Future
import scala.concurrent.duration._
import akka.actor._
import akka.util.Timeout
import porter.model.Ident
import org.eknet.sitebag._
import org.eknet.sitebag.content.{ExtractedContent, Content}
import org.eknet.sitebag.mongo.ReextractEntryWorker.ExtractJob

class ReextractActor(extrRef: ActorRef, mongo: SitebagMongo) extends Actor with ActorLogging {
  private var account2Worker = Map.empty[Ident, ActorRef]
  private var worker2Account = Map.empty[ActorRef, Ident]

  import context.dispatcher
  private val worker = context.actorOf(ReextractEntryWorker(extrRef, mongo), "extract-entry")

  def receive = {
    case ReExtractContent(account, None) =>
      if (account2Worker contains account) {
        sender ! Failure("A re-extraction job is already running for you.")
      } else {
        val w = context.watch(context.actorOf(Props(
          new ReextractAllWorker(worker, account, mongo)),
          s"${account.name}-extraction"))
        account2Worker += (account → w)
        worker2Account += (w → account)
        w forward "start"
        sender ! Success(s"Re-extraction started for '${account.name}'.")
      }

    case ReExtractContent(account, Some(entryId)) =>
      if (account2Worker contains account) {
        sender ! Failure("A re-extraction job is already running for you.")
      } else {
        worker forward ExtractJob(account, entryId)
      }

    case req@ReExtractStatusRequest(account) ⇒
      account2Worker.get(account) match {
        case Some(w) ⇒ w forward req
        case None    ⇒ sender ! Success(ReExtractStatus.Idle(account))
      }

    case Terminated(ref) =>
      if (worker2Account contains ref) {
        val account = worker2Account(ref)
        worker2Account -= ref
        account2Worker -= account
        context.system.eventStream.publish(ReextractionDone(account))
      }
  }
}
object ReextractActor {
  def apply(extrRef: ActorRef, mongo: SitebagMongo): Props = Props(classOf[ReextractActor], extrRef, mongo)
}


class ReextractAllWorker(worker: ActorRef, account: Ident, mongo: SitebagMongo) extends Actor with ActorLogging {

  import context.dispatcher
  import ReextractAllWorker.Stats

  private [this] val stats = new Stats(account)

  case class Finished(msg: String, error: Option[Throwable]) extends Serializable
  case class PushDone(n: Int)

  def receive = {
    case "start" =>
      val client = sender
      stats.init()
      context.become(working(client))
      context.setReceiveTimeout(2.minutes)
      log.info(s"Starting re-extraction job for ${account.name}.")
      val f = mongo.withPageMetaEntries(account) { entry =>
        worker ! ExtractJob(account, entry.id)
      }
      f.onComplete {
        case scala.util.Success(n) =>
          log.info(s"Pushed $n extraction jobs for '${account.name}'. Waiting for completion.")
          self ! PushDone(n)

        case scala.util.Failure(e) =>
          self ! Finished(s"Extraction for '${account.name}' failed", Some(e))
      }

    case ReceiveTimeout =>
      log.warning("Timeout starting extraction.")
      context.stop(self)
  }

  def working(client: ActorRef): Receive = {
    case PushDone(total) =>
      stats.total = Some(total)

    case r: Result[_] =>
      r match {
        case Failure(msg, Some(ex)) ⇒
          log.error(ex, "Error during extraction")
        case Failure(msg, None) ⇒
          log.error("Error during extraction: "+ msg)
        case _ ⇒
      }
      stats.next(r)
      log.debug(s"Reextraction '${account.name}': [${stats.done}|${stats.failed}]/${stats.total} entries done.")
      if (stats.isDone) {
        self ! Finished(s"Re-extraction for '${account.name}' completed.", None)
      }

    case ReExtractStatusRequest(_) ⇒
      sender ! Success(stats.toStatus)

    case Finished(msg, error) =>
      error.map(e => log.error(msg, e)).getOrElse(log.info(msg))
      client ! error.map(Failure.apply).getOrElse(Success(stats.toStatus, msg))
      context.stop(self)

    case ReceiveTimeout =>
      log.warning("Timeout during extraction: "+ stats.toStatus)
      client ! Failure("Timeout extracting entries. Process cancelled.")
      context.stop(self)
  }
}
object ReextractAllWorker {

  private final class Stats(account: Ident) {
    var done: Int = 0
    var failed: Int = 0
    var total: Option[Int] = None
    var startedAt: Long = -1
    var finishedAt: Option[Long] = None

    def next(r: Result[_]) = {
      if (r.isSuccess) done += 1;
      else failed += 1;

      for (t <- total; if (done + failed) >= t) {
        finishedAt = Some(System.currentTimeMillis())
      }
      done
    }
    def init() = {
      done = 0
      failed = 0
      total = None
      finishedAt = None
      startedAt = System.currentTimeMillis()
    }
    def isDone = finishedAt.isDefined
    def toStatus = ReExtractStatus.Running(account, done, total, startedAt)
  }
}


class ReextractEntryWorker(extrRef: ActorRef, mongo: SitebagMongo) extends Actor with ActorLogging {
  import akka.pattern.ask
  import akka.pattern.pipe
  import context.dispatcher

  type ExtractResult = Result[ExtractedContent]

  def extractContent(contentResult: Result[Content]): Future[ExtractResult] = {
    implicit val extractTimeout: Timeout = 5.minutes
    contentResult match {
      case Success(Some(c), _) =>
        (extrRef ? c).mapTo[ExtractResult]

      //todo get rid of this repetition success(none) , failure
      case s@Success(None, msg) =>
        Future.successful(Success(None, msg))
      case f: Failure =>
        val msg = s"Re-extraction failed: ${f.message}"
        log.warning(msg)
        Future.successful(f)
    }
  }

  def updateExtractedContent(account: Ident, entryId: String, result: ExtractResult): Future[Ack] = {
    result match {
      case Success(Some(extr), _) =>
        val f = mongo.updateEntryContent(account, entryId, extr.title, extr.text, extr.shortText)
        f onSuccess { case _ =>
          context.system.eventStream.publish(EntryContentsChange(account, entryId))
        }
        f

      //todo get rid of this repetition success(none) , failure
      case s@Success(None, msg) =>
        Future.successful(Success(None, msg))
      case f: Failure =>
        val msg = s"Update re-extracted content failed for entry '${account.name}:$entryId': ${f.message}"
        log.warning(msg)
        Future.successful(f)
    }
  }

  def receive = {
    case ExtractJob(account, entryId) =>
      val f = for {
        content <- mongo.getEntryContent(account, entryId)
        extract <- extractContent(content)
        result  <- updateExtractedContent(account, entryId, extract)
      } yield result
      (f.recover { case ex ⇒ Failure(ex) }) pipeTo sender
  }
}
object ReextractEntryWorker {
  /** Needs an [[org.eknet.sitebag.ExtractionActor]] */
  def apply(extrRef: ActorRef, mongo: SitebagMongo) = Props(classOf[ReextractEntryWorker], extrRef, mongo)

  /** Answers with [[org.eknet.sitebag.Ack]] to these messages. */
  case class ExtractJob(account: Ident, entryId: String)
}
