package org.eknet.sitebag.mongo

import scala.concurrent.duration._
import akka.actor._
import porter.model.Ident
import org.eknet.sitebag._
import org.eknet.sitebag.content.{ExtractedContent, Content}
import org.eknet.sitebag.mongo.ReextractEntryWorker.ExtractJob
import scala.Some
import org.eknet.sitebag.ReExtractContent
import akka.util.Timeout
import scala.concurrent.Future

class ReextractActor(extrRef: ActorRef, dbname: Option[String]) extends Actor with ActorLogging {
  private var account2Worker = Map.empty[Ident, ActorRef]
  private var worker2Account = Map.empty[ActorRef, Ident]
  private val settings = SitebagSettings(context.system)

  import context.dispatcher
  private val mongo = dbname.map(settings.makeMongoClient) getOrElse settings.defaultMongoClient
  private val worker = context.actorOf(ReextractEntryWorker(extrRef, mongo), "extract-entry")

  def receive = {
    case ReExtractContent(account, None) =>
      if (account2Worker contains account) {
        sender ! Failure("A re-extraction job is already running for you.")
      } else {
        val w = context.watch(context.actorOf(Props(
          new ReextractAllWorker(worker, account, mongo)),
          s"${account.name}-extraction"))
        account2Worker += (account -> w)
        worker2Account += (w -> account)
        w forward "start"
      }

    case ReExtractContent(account, Some(entryId)) =>
      worker forward ExtractJob(account, entryId)

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
  def apply(extrRef: ActorRef): Props = Props(classOf[ReextractActor], extrRef, None)
  def apply(extrRef: ActorRef, dbname: String): Props = Props(classOf[ReextractActor], extrRef, Some(dbname))
}
class ReextractAllWorker(worker: ActorRef, account: Ident, mongo: SitebagMongo) extends Actor with ActorLogging {

  context.setReceiveTimeout(10.minutes)
  import context.dispatcher

  private[this] var numberOfJobs = -1
  private[this] val numberOfJobsDone = Iterator from 0

  case class Finished(msg: String, error: Option[Throwable]) extends Serializable
  case class PushDone(n: Int)

  def receive = {
    case "start" =>
      val client = sender
      context.become(working(client))
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
    case PushDone(n) =>
      numberOfJobs = n

    case r: Result[_] =>
      val n = numberOfJobsDone.next()
      log.debug(s"Reextraction: ${n+1} entries done.")
      if (n == numberOfJobs -1) {
        self ! Finished(s"Re-extraction for '${account.name}' done.", None)
      }

    case Finished(msg, error) =>
      error.map(e => log.error(msg, e)).getOrElse(log.info(msg))
      client ! error.map(Failure.apply).getOrElse(Success(msg))
      context.stop(self)

    case ReceiveTimeout =>
      client ! Failure("Timeout extracting entries. Process cancelled.")
      context.stop(self)
  }
}

class ReextractEntryWorker(extrRef: ActorRef, mongo: SitebagMongo) extends Actor with ActorLogging {
  import akka.pattern.ask
  import akka.pattern.pipe
  import context.dispatcher

  private implicit val timeout: Timeout = 10.seconds
  type ExtractResult = Result[ExtractedContent]

  def extractContent(contentResult: Result[Content]): Future[ExtractResult] = {
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
      f pipeTo sender
  }
}
object ReextractEntryWorker {
  /** Needs an [[org.eknet.sitebag.ExtractionActor]] */
  def apply(extrRef: ActorRef, mongo: SitebagMongo) = Props(classOf[ReextractEntryWorker], extrRef, mongo)

  /** Answers with [[org.eknet.sitebag.Ack]] to these messages. */
  case class ExtractJob(account: Ident, entryId: String)
}
