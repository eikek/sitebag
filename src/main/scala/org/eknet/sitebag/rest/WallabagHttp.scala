package org.eknet.sitebag.rest

import spray.routing.{Directives, Route}
import akka.event.Logging
import akka.actor.{ActorRefFactory, ActorRef}
import scala.concurrent.ExecutionContext
import akka.util.Timeout
import akka.pattern.ask
import org.eknet.sitebag._
import spray.httpx.encoding.Gzip
import org.eknet.sitebag.model.{Page, Token, Tag, PageEntry}
import porter.model.PasswordCredentials
import org.eknet.sitebag.ListEntries
import org.parboiled.common.Base64
import spray.http.{Uri, StatusCodes}

class WallabagHttp(val settings: SitebagSettings, appRef: ActorRef, refFactory: ActorRefFactory, ec: ExecutionContext, to: Timeout)
  extends Directives with RestDirectives {

  implicit def timeout = to
  implicit def executionContext = ec
  implicit def factory = refFactory

  def porter = settings.tokenContext

  def route(subject: String): Route = {
    parameter("action" ! "add", "url") { url =>
      val uri = new String(Base64.rfc2045().decode(url), "UTF-8")
      checkAccess(subject, settings.porter, checkAddEntry) { rctx =>
        onSuccess(appRef ? Add(rctx.subject, ExtractRequest(uri.trim))) { _ =>
          redirect("/ui/", StatusCodes.Found)
        }
      }
    } ~
    parameter("type".?, "token") { (tag, token) =>
      authenticateToken(subject, Token(token), Set(s"sitebag:$subject:entry:get")) { rctx =>
        encodeResponse(Gzip) {
          wbRssEntries(settings.wbUrl(subject), appRef, mapWallabagType(tag), rctx)
        }
      }
    }
  }

  def mapWallabagType(name: Option[String]) = {
    val page = Some(Page(1, Some(25)))
    name.map(_.toLowerCase) match {
      case Some("fav") => EntrySearch(TagInput(Set(Tag.favourite)), Some(false), "", page, true)
      case Some("home") => EntrySearch(TagInput(Set.empty), Some(false), "", page, true)
      case Some("archive") => EntrySearch(TagInput(Set.empty), Some(true), "", page, true)
      case _ => EntrySearch(TagInput(Set.empty), Some(false), "", page, true)
    }
  }
  def wbRssEntries(uri: Uri, appRef: ActorRef, sq: EntrySearch, rctx: RestContext): Route = {
    import akka.pattern.ask
    import RssSupport._
    val page = sq.page.getOrElse(Page(1, Some(25)))
    val f = (appRef ? sq.toListEntries(rctx.subject).copy(page = page)).mapTo[Result[List[PageEntry]]]
    complete {
      f.map(res => mapRss(uri, rctx.subject, sq, res)(settings.entryUiUri))
    }
  }

}
