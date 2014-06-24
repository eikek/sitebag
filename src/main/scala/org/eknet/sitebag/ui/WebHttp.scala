package org.eknet.sitebag.ui

import spray.http.StatusCodes
import spray.routing.{Directives, Route}
import akka.actor.{ActorRef, ActorRefFactory}
import akka.util.Timeout
import org.eknet.sitebag.rest.RestDirectives
import org.eknet.sitebag.model.UserInfo
import org.eknet.sitebag._

class WebHttp(val settings: SitebagSettings, store: ActorRef, refFactory: ActorRefFactory, to: Timeout)
  extends Directives with RestDirectives with WebDirectives {
  implicit def timeout = to
  implicit def executionContext = refFactory.dispatcher
  implicit def actorSystem = refFactory

  val webSettings = settings.makeSubconfig("webui", c => new WebSettings(settings, c))

  def route: Route = if (settings.webuiEnabled) enabled else disabled

  def disabled: Route = reject()

  import org.eknet.sitebag.rest.JsonProtocol._
  import spray.httpx.SprayJsonSupport._

  def enabled: Route = {
    path("static" / Segment) { file =>
      getFromResource("org/eknet/sitebag/ui/static/" +file)
    } ~
    path("login") {
      render(UserInfo.empty, "Login", html.login())
    } ~
    authcUiOrLogin { userInfo =>
      pathEndOrSingleSlash {
        render(userInfo, "Search", html.dashboard(webSettings))
      } ~
      path("conf") {
        render(userInfo, "Configuration", html.configuration(userInfo, webSettings))
      } ~
      path("entry" / Segment) { id =>
        getEntry(store, GetEntry(userInfo.name, id)) { entry =>
          render(userInfo, entry.title, html.entryview(entry, webSettings))
        }
      } ~
      path("entry" / Segment / "cache") { id =>
        getEntryContent(store, GetEntryContent(userInfo.name, id))
      } ~
      path("api" / "set-theme") {
        anyParam("theme") { url =>
          send("Theme changed.", "Unable to change theme.") {
            settings.porter.updateAccount(userInfo.name, a => a.updatedProps(UserInfo.themeUrl.set(url)))
          }
        }
      } ~
      path("api" / "bootswatch") {
        compressResponse() {
          complete{
            import spray.client.pipelining._
            (Get(webSettings.bootswatchApi) ~> sendReceive).map(_.entity)
          }
        }
      } ~
      render(userInfo, "Not found", html.notfound())
    }
  }
}
