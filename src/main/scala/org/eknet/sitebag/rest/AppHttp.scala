package org.eknet.sitebag.rest

import scala.concurrent.ExecutionContext
import akka.actor.{ActorRefFactory, ActorRef}
import akka.pattern.ask
import akka.util.Timeout
import spray.routing.{Route, Directives}
import spray.httpx.SprayJsonSupport
import spray.http._
import org.eknet.sitebag._
import org.eknet.sitebag.model._
import org.eknet.sitebag.ToggleArchived
import porter.app.client.PorterContext
import porter.model.Ident
import spray.httpx.marshalling.ToResponseMarshaller

class AppHttp(val settings: SitebagSettings, appRef: ActorRef, refFactory: ActorRefFactory, ec: ExecutionContext, to: Timeout)
  extends Directives with RestDirectives with FormUnmarshaller {

  implicit def timeout = to
  implicit def executionContext = ec

  import spray.json._
  import JsonProtocol._
  import SprayJsonSupport._

  def getEntry(porter: PorterContext, subject: String, entryid: String, full: Boolean): Route = {
    checkAccess(subject, porter, checkGetEntry(entryid)) { rctx =>
      if (! full) {
        complete {
          (appRef ? GetEntryMeta(rctx.subject, entryid)).mapTo[Result[PageEntry]]
        }
      } else {
        complete {
          (appRef ? GetEntry(rctx.subject, entryid)).mapTo[Result[PageEntry]]
        }
      }
    }
  }

  def listEntries[A](subject: String, transform: (EntrySearch, Result[List[PageEntry]]) => A)(implicit rm: ToResponseMarshaller[A]): Route = {
    fromParams(EntrySearch) { search =>
      complete {
        val f = (appRef ? search.toListEntries(subject))
          .mapTo[Result[List[PageEntry]]]
        f.map(result => transform(search, result))
      }
    }
  }

  def route(subject: String): Route = {
    path("entry") {
      // add urls to sitebag
      (put | post) {
        checkAccess(subject, settings.porter, checkAddEntry) { rctx =>
          handle { radd: RAdd =>
            (appRef ? Add(rctx.subject, ExtractRequest(radd.url), radd.title, radd.tags)).mapTo[Result[String]]
          }
        }
      } ~
      get {
        // this is a special handler for the bookmarklet. it will respond with a
        // little javascript alert
        parameters("url", "add") { (url, _) =>
          checkAccess(subject, settings.porter, checkAddEntry) { rctx =>
            val f = (appRef ? Add(rctx.subject, ExtractRequest(url))).mapTo[Result[String]]
            onSuccess(f) { result =>
              respondWithMediaType(MediaTypes.`application/javascript`) {
                complete {
                  "alert('"+ result.message +"');"
                }
              }
            }
          }
        }
      }
    } ~
    path("entry" / Segment) { id =>
      // gets a single entry with complete content by default, if `complete=false`
      // the full content is not sent
      get {
        parameter('complete.as[Boolean] ? true) { full =>
          getEntry(settings.tokenContext, subject, id, full)
        }
      } ~
      // deletes a entry by its id
      delete {
        checkAccess(subject, settings.porter, checkDeleteEntry(id)) { rctx =>
          complete {
            (appRef ? DropEntry(rctx.subject, id)).mapTo[Ack]
          }
        }
      } ~
      post {
        checkAccess(subject, settings.porter, checkDeleteEntry(id)) { rctx =>
          handle { _: DeleteAction =>
            (appRef ? DropEntry(rctx.subject, id)).mapTo[Ack]
          }
        }
      }
    } ~
    path("entry" / Segment / "togglearchived") { id =>
      // toggles the archived flag of an entry
      post {
        checkAccess(subject, settings.porter, checkUpdateEntry(id)) { rctx =>
          complete {
            (appRef ? ToggleArchived(rctx.subject, id)).mapTo[Result[Boolean]]
          }
        }
      }
    } ~
    path("entry" / Segment / "setarchived") { id =>
      // sets the archived flag of the entry to `true`
      post {
        checkAccess(subject, settings.porter, checkUpdateEntry(id)) { rctx =>
          handle { flag: Flag =>
            (appRef ? SetArchived(rctx.subject, id, flag.flag)).mapTo[Result[Boolean]]
          }
        }
      }
    } ~
    path("entry" / Segment / "tag") { id =>
      // adds all given tags to this entry
      post {
        checkAccess(subject, settings.porter, checkUpdateEntry(id)) { rctx =>
          handle { tagin: TagInput =>
            (appRef ? TagEntry(rctx.subject, id, tagin.tags)).mapTo[Ack]
          }
        }
      }
    } ~
    path("entry" / Segment / "untag") { id =>
      // removes all given tags from this entry
      post {
        checkAccess(subject, settings.porter, checkUpdateEntry(id)) { rctx =>
          handle { tagin: TagInput =>
            (appRef ? UntagEntry(rctx.subject, id, tagin.tags)).mapTo[Ack]
          }
        }
      }
    } ~
    path("entry" / Segment / "tags") { id =>
      // removes all tags from this entry and adds the given tags
      post {
        checkAccess(subject, settings.porter, checkUpdateEntry(id)) { rctx =>
          handle { tagin: TagInput =>
            (appRef ? SetTags(subject, id, tagin.tags)).mapTo[Ack]
          }
        }
      }
    } ~
    path("entry" / Segment / "cache") { id =>
      // return the original page from cache
      get {
        checkAccess(subject, settings.tokenContext, checkGetEntry(id)) { rctx =>
          getEntryContent(appRef, GetEntryContent(rctx.subject, id))
        }
      }
    } ~
    path("tags") {
      // returns a "tag cloud"
      get {
        checkAccess(subject, settings.porter, checkListTags) { rctx =>
          fromParams(TagFilter) { f =>
            complete {
              (appRef ? ListTags(rctx.subject, f.filter)).mapTo[Result[TagList]]
            }
          }
        }
      }
    } ~
    // returns a list of entries as json
    path("entries" / "json") {
      get {
        checkAccess(subject, settings.tokenContext, checkGetEntries) { rctx =>
          listEntries(subject, (s, r) => r)
        }
      }
    } ~
    // returns a list of entries as rss xml
    path("entries" / "rss") {
      import RssSupport._
      get {
        checkAccess(subject, settings.tokenContext, checkGetEntries) { rctx =>
          listEntries(subject, (search, res) => {
            val uri = settings.rssFeedUrl(subject, rctx.token.get, search)
            mapRss(uri, rctx.subject, search, res)(settings.entryUiUri)
          })
        }
      }
    } ~
    path("entries" / "rss" / Segment) { token =>
      // returns a  list of entries as rss xml and authenticates the request with
      // the token given as last part in the url path
      get {
        import RssSupport._
        authenticateToken(subject, Token(token), Set(s"sitebag:${subject.name}:entry:get")) { rctx =>
          listEntries(subject, (search, res) => {
            val uri = settings.rssFeedUrl(subject, rctx.token.get, search)
            mapRss(uri, rctx.subject, search, res)(settings.entryUiUri)
          })
        }
      }
    }
  }
}
