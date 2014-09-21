package org.eknet.sitebag.ui

import spray.http.StatusCodes
import spray.routing.{Directives, Route}
import akka.actor.{ActorRef, ActorRefFactory}
import akka.util.Timeout
import org.eknet.sitebag.rest.RestDirectives
import org.eknet.sitebag.model.UserInfo
import org.eknet.sitebag.model.PageEntry
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
      renderLoginPage
    } ~
    authcUiOrLogin { userInfo =>
      pathEndOrSingleSlash {
        renderDashboard(userInfo)
      } ~
      path("conf") {
        renderConfiguration(userInfo)
      } ~
      path("entry" / Segment) { id =>
        getEntry(store, GetEntry(userInfo.name, id)) { entry =>
          renderEntry(userInfo, entry)
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
      } ~ {
        respondWithStatus(StatusCodes.NotFound) {
          render(userInfo, "Not found", html.notfound(), webSettings)
        }
      }
    }
  }

  private def renderEntry(userInfo: UserInfo, entry: PageEntry) =
    render(userInfo, entry.title, html.entryview(entry, webSettings),
      webSettings, "entryview" :: Nil)

  private def renderLoginPage =
    render(UserInfo.empty, "Login", html.login(), webSettings, "login" :: Nil)

  private def renderConfiguration(userInfo: UserInfo) =
    render(userInfo, "Configuration", html.configuration(userInfo, webSettings),
      webSettings, "configuration" :: Nil)

  private def renderDashboard(info: UserInfo) =
    render(info, "Search", html.dashboard(webSettings), webSettings, "dashboard" :: Nil)

}
