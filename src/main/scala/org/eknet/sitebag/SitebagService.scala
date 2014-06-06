package org.eknet.sitebag

import scala.concurrent.duration._
import spray.routing.{Directive0, HttpServiceActor}
import akka.actor.{Props, Actor, ActorLogging}
import akka.util.Timeout
import porter.app.akka.telnet.TelnetServer
import org.eknet.sitebag.rest._
import org.eknet.sitebag.ui.WebHttp
import akka.event.Logging
import akka.routing.RoundRobinRouter
import org.eknet.sitebag.mongo.{SitebagMongo, ReextractActor, MongoStoreActor}
import org.eknet.sitebag.search.SearchActor

class SitebagService extends HttpServiceActor with Actor with ActorLogging with RestDirectives {
  implicit val timeout: Timeout = 10.seconds
  implicit def executionContext = context.dispatcher
  implicit val s = context.system

  val settings = SitebagSettings(context.system)
  val mongo = SitebagMongo(settings)

  private val nCpu = Runtime.getRuntime.availableProcessors()
  val extractor = context.actorOf(ExtractionActor().withRouter(RoundRobinRouter(nCpu)), "extractors")
  val reextractor = context.actorOf(ReextractActor(extractor, mongo), "re-extractor")
  val store = context.actorOf(MongoStoreActor(mongo), "mongo-store")
  val httpclient = context.actorOf(HttpClientActor(extractor), "http-client")
  val search = context.actorOf(SearchActor(mongo), "search")
  val app = context.actorOf(AppActor(httpclient, store, search, settings), "app")

  val admin = context.actorOf(AdminActor(reextractor, settings.porter, mongo, settings), "admin")
  val adminHttp = new AdminHttp(settings, admin, executionContext, timeout)
  val appHttp = new AppHttp(settings, app, context, executionContext, timeout)
  val webHttp = new WebHttp(settings, store, context, timeout)
  val binHttp = new BinaryHttp(settings, store, context, executionContext, timeout)
  val wallabag = new WallabagHttp(settings, app, context, executionContext, timeout)

  if (settings.porterModeIsEmbedded && settings.telnetEnabled) {
    TelnetServer.bind(settings.porter.porterRef, settings.telnetHost, settings.telnetPort)
  }

  def receive = runRoute {
    reqreslog {
      pathPrefix("wb" / Segment) { subject =>
        wallabag.route(subject)
      } ~
      pathPrefix("api" / Segment) { subject =>
        adminHttp.route(subject, settings.porter) ~ appHttp.route(subject)
      } ~
      pathPrefix("ui") {
        webHttp.route
      } ~
      pathPrefix("bin") {
        binHttp.route
      }
    }
  }

  def reqreslog: Directive0 = {
    if (settings.logRequests) CommonDirectives.logRequestResponse("Cycle" -> Logging.InfoLevel)
    else CommonDirectives.pass
  }
}
object SitebagService {
  def apply(): Props = Props(classOf[SitebagService])
}
