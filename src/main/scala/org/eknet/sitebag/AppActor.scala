package org.eknet.sitebag

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.concurrent.Future
import akka.actor.{Props, ActorRef, Actor, ActorLogging}
import akka.util.Timeout
import porter.model.Ident
import org.eknet.sitebag.content.{ExtractedContent, Content}
import org.eknet.sitebag._
import org.eknet.sitebag.AppActor.FetchPageWorker
import org.eknet.sitebag.model.{PageEntry, FullPageEntry, Binary}
import org.jsoup.Jsoup

class AppActor(clientRef: ActorRef, store: ActorRef, search: ActorRef, settings: SitebagSettings) extends Actor with ActorLogging {
  import akka.pattern.ask
  import akka.pattern.pipe
  import context.dispatcher
  implicit val timeout: Timeout = 10.seconds

  def receive = {
    case req: Add =>
      val worker = context.actorOf(Props(new FetchPageWorker(clientRef, store)))
      worker forward req

    case r: GetEntry =>
      val rewrite = AppActor.rewriteLinks(settings, store, r.account)_
      val f = (store ? r).mapTo[Result[PageEntry]].flatMap {
        case Success(Some(e), _) => rewrite(e)
        case x => Future.successful(x)
      }
      f pipeTo sender

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
      val f = if (req.query.isEmpty) {
        (store ? req).mapTo[Result[List[PageEntry]]]
      } else {
        (search ? req).mapTo[Result[List[PageEntry]]]
      }
      if (req.complete) {
        val rewrite = AppActor.rewriteLinks(settings, store, req.account)_
        val rf: Future[Result[List[PageEntry]]] = f.flatMap {
          case Success(Some(list), _) => Future.sequence(list.map(rewrite)).map(Result.flatten)
          case x => Future.successful(x)
        }
        rf pipeTo sender
      } else {
        f pipeTo sender
      }

    case req: GetBinaryById =>
      store forward req

    case req: GetBinaryByUrl =>
      store forward req
  }
}

object AppActor {
  def apply(clientRef: ActorRef, storeRef: ActorRef, searchRef: ActorRef, settings: SitebagSettings) =
    Props(classOf[AppActor], clientRef, storeRef, searchRef, settings)


  def rewriteLinks(settings: SitebagSettings, store: ActorRef, account: Ident)(entry: PageEntry)(implicit ec: ExecutionContext, to: Timeout): Future[Result[PageEntry]] = {
    import scala.collection.JavaConverters._
    import akka.pattern.ask

    val doc = Jsoup.parseBodyFragment(entry.content)
    val fs = Future.sequence(for (e <- doc.select("a[href]").iterator().asScala.toSeq) yield {
      val id = PageEntry.makeId(e.attr("href"))
      (store ? GetEntry(account, id)).mapTo[Result[PageEntry]].map(r => e -> r)
    })
    fs.map { seq =>
      seq.foreach {
        case (el, Success(Some(en), _)) =>
          val url = el.attr("href")
          el.attr("href", settings.uiUri("entry/"+en.id).toString())
          el.attr("title", url)
        case (el, Success(None, _)) =>
          val adde = el.clone()
          adde.attr("href", "#")
          adde.attr("class", "sb-add-entry-link")
          adde.attr("data-id", el.attr("href"))
          adde.attr("title", "Add to SiteBag")
          adde.html("&nbsp;<span class=\"glyphicon glyphicon-download-alt small\"></span>")
          el.after(adde)
        case _ =>
      }
      Success(entry.copy(content = doc.body().html()))
    }
  }


  class FetchPageWorker(clientRef: ActorRef, store: ActorRef) extends Actor with ActorLogging {
    import akka.pattern.ask
    import context.dispatcher
    implicit val timeout: Timeout = 10.seconds
    context.setReceiveTimeout(20.seconds)

    def receive = idle

    def idle: Receive = {
      case req: Add =>
        context.become(waitForPage(req, sender))
        store ! GetEntryMeta(req.account, PageEntry.makeId(req.page.url))
    }

    def waitForPage(req: Add, client: ActorRef): Receive = {
      case Success(Some(_), _) =>
        log.debug("Document already exist. Do not fetch "+ req.page.url +" for "+ req.account.name)
        client ! Success(PageEntry.makeId(req.page.url), "Document does already exist.")
        context.stop(self)

      case _ =>
        log.debug(s"About to fetch page at ${req.page.url}")
        clientRef ! req.page
        context.become(waitforContent(req, client))
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
        client ! Success(pentry.entry.id, saved.message)
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
