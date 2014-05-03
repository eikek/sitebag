package org.eknet.sitebag

import scala.concurrent.duration._
import akka.actor.{Props, ActorRef, Actor, ActorLogging}
import org.eknet.sitebag.content.{ExtractedContent, Content}
import org.eknet.sitebag._
import akka.util.Timeout
import org.eknet.sitebag.AppActor.FetchPageWorker
import org.eknet.sitebag.model.{PageEntry, FullPageEntry, Binary}

class AppActor(clientRef: ActorRef, store: ActorRef) extends Actor with ActorLogging {
  import akka.pattern.ask
  import akka.pattern.pipe
  import context.dispatcher
  implicit val timeout: Timeout = 10.seconds

  def receive = {
    case req: Add =>
      val worker = context.actorOf(Props(new FetchPageWorker(clientRef, store)))
      worker forward req

    case r: GetEntry =>
      store forward r

    case r: GetEntryMeta =>
      store forward r

    case r: GetEntryContent =>
      store forward r

    case r@ DropEntry(account, entryId) =>
      val f = (store ? r).mapTo[Ack]
      f onSuccess { case ack if ack.isSuccess =>
        context.system.eventStream.publish(EntryDropped(account, entryId))
      }
      f pipeTo sender

    case req: ToggleArchived =>
      val f = (store ? req).mapTo[Result[Boolean]]
      f onSuccess {
        case Success(Some(flag), _) =>
          context.system.eventStream.publish(EntryArchivedChange(req.account, req.entryId, flag))
      }
      f pipeTo sender

    case req: SetArchived =>
      val f = (store ? req).mapTo[Result[Boolean]]
      f onSuccess {
        case Success(Some(flag), _) =>
          context.system.eventStream.publish(EntryArchivedChange(req.account, req.entryId, flag))
      }
      f pipeTo sender

    case req: TagEntry =>
      val f = (store ? req).mapTo[Ack]
      f onSuccess { case r if r.isSuccess =>
        context.system.eventStream.publish(EntryTagged(req.account, req.entryId, req.tags, added = true))
      }
      f pipeTo sender
    case req: UntagEntry =>
      val f = (store ? req).mapTo[Ack]
      f onSuccess { case r if r.isSuccess =>
        context.system.eventStream.publish(EntryUntagged(req.account, req.entryId, req.tags))
      }
      f pipeTo sender

    case req: GetTags =>
      store forward req

    case req@ SetTags(account, entryId, tags) =>
      val f = (store ? req).mapTo[Ack]
      f onSuccess { case r if r.isSuccess =>
        context.system.eventStream.publish(EntryTagged(account, entryId, tags, added = false))
      }
      f pipeTo sender

    case req: ListTags =>
      store forward req

    case req: ListEntries =>
      store forward req

    case req: GetBinaryById =>
      store forward req

    case req: GetBinaryByUrl =>
      store forward req
  }
}

object AppActor {
  def apply(clientRef: ActorRef, storeRef: ActorRef) = Props(classOf[AppActor], clientRef, storeRef)


  class FetchPageWorker(clientRef: ActorRef, store: ActorRef) extends Actor with ActorLogging {
    import akka.pattern.ask
    import context.dispatcher
    implicit val timeout: Timeout = 10.seconds
    context.setReceiveTimeout(20.seconds)

    def receive = idle

    def idle: Receive = {
      case req: Add =>
        log.debug(s"About to fetch page at ${req.page.url}")
        clientRef ! req.page
        context.become(waitforContent(req, sender))
    }

    def waitforContent(add: Add, client: ActorRef): Receive = {
      case s@Success(Some(extracted: ExtractedContent), _) =>
        log.debug(s"Successful response from ${extracted.original.uri}")
        val full = makePageEntry(extracted)
        val newtitle = Option(full.entry.title).filter(_.nonEmpty).orElse(add.title).getOrElse("No title")
        store ! AddEntry(add.account, full.copy(entry = full.entry.copy(title = newtitle)))
        context.become(waitForSave(add, extracted, client))

      case f@Failure(msg, error) =>
        error.foreach { t => log.error(t, s"Error extracting content from page '${add.page.url}'")}
        if (error.isEmpty) {
          log.error(s"Error extracting content from page '${add.page.url}'")
        }
        client ! f
        context.stop(self)
    }

    def waitForSave(add: Add, result: ExtractedContent, client: ActorRef): Receive = {
      case saved: Result[_] if saved.isSuccess =>
        val pentry = makePageEntry(result)
        //tag new entry
        if (add.tags.nonEmpty) {
          store ! TagEntry(add.account, pentry.entry.id, add.tags)
        }
        //store all binary things in the text; if some fail, we don't care
        log.debug(s"About to retrieve and save ${result.binaryUrls.size} resources...")
        result.binaryUrls.foreach { url =>
          val f = (clientRef ? FetchContent(url)).mapTo[Content]
          f.onSuccess { case content =>
            log.debug(s"Saving binary at $url")
            store ! AddBinary(add.account, pentry.entry.id, Binary(content))
          }
          f.onFailure { case t =>
            log.error(t, s"Error fetching contents from $url")
          }
        }
        context.system.eventStream.publish(EntrySaved(add.account, pentry))
        client ! Success(pentry.entry.id, "Document saved successfully.")
        context.stop(self)

      case notsaved@ Failure(msg, error) =>
        error.map(e => log.error(e, "Error when storing page entry: "+msg)) getOrElse {
          log.error("Error when storing page entry: "+msg)
        }
        client ! notsaved
        context.stop(self)
    }

    def makePageEntry(s: ExtractedContent): FullPageEntry =
      FullPageEntry(PageEntry(s.title, s.original.uri, s.text, s.shortText), s.original)

  }
}
