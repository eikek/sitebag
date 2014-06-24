package org.eknet.sitebag.rest

import akka.actor.{ActorRef, ActorRefFactory}
import akka.pattern.ask
import akka.util.Timeout
import org.eknet.sitebag._
import org.eknet.sitebag.model.{Page, PageEntry, Token, Tag}
import org.eknet.sitebag.ui.{WebDirectives, WebSettings}
import org.parboiled.common.Base64
import scala.concurrent.ExecutionContext
import spray.http.{StatusCodes, Uri}
import spray.httpx.encoding.Gzip
import spray.routing.Directives
import spray.routing.Route

class WallabagHttp(val settings: SitebagSettings, appRef: ActorRef, refFactory: ActorRefFactory, ec: ExecutionContext, to: Timeout)
  extends Directives with RestDirectives with WebDirectives {

  implicit def timeout = to
  implicit def executionContext = ec
  implicit def factory = refFactory

  val webSettings = settings.makeSubconfig("webui", c => new WebSettings(settings, c))

  def route(subject: String): Route = {
    parameter("action" ! "add", "url") { url =>
      val uri = new String(Base64.rfc2045().decode(url), "UTF-8")
      checkAccessOrLogin(subject, checkAddEntry) { userinfo =>
        onSuccess(appRef ? Add(userinfo.name, ExtractRequest(uri.trim))) { _ =>
          redirect("/ui/", StatusCodes.Found)
        }
      }
    } ~
    parameter("type".?, "token") { (tag, token) =>
      checkToken(subject, Token(token), checkGetEntries) { rctx =>
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
