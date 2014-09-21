package org.eknet.sitebag.ui

import org.eknet.sitebag.rest.RestContext
import porter.app.client.PorterContext
import porter.model.Account
import scala.concurrent.Future
import spray.http.StatusCodes
import spray.routing.Directive0
import spray.routing.{Directives, Route, Directive1}
import akka.pattern.ask
import porter.client.messages.OperationFinished
import org.eknet.sitebag._
import org.eknet.sitebag.rest.{RestDirectives, AuthDirectives}
import org.eknet.sitebag.rest.permission
import org.eknet.sitebag.model.{PageEntry, Token, UserInfo}
import org.eknet.sitebag.content.Content
import akka.actor.ActorRef
import spray.http.{HttpEntity, StatusCodes, HttpResponse, ContentTypes}
import twirl.api.Html

trait WebDirectives extends Directives {
  self: RestDirectives  =>

  import org.eknet.sitebag.rest.JsonProtocol._
  import spray.httpx.SprayJsonSupport._
  import TwirlSupport._

  def webSettings: WebSettings

  def render(info: UserInfo, title: String, body: Html, js: List[String] = Nil): Route = {
    complete {
      html.layout(info, webSettings, title, body, js)
    }
  }

  def authcUi: Directive1[UserInfo] = {
    for {
      acc <- authenticateWithCookie
      canCreate <- hasPerm(settings.porter, acc.name, permission.createUser)
    } yield {
      val token = UserInfo.token.get(acc.props)
      UserInfo(acc.name.name, acc.props, token, canCreate)
    }
  }

  def authcUiOrLogin: Directive1[UserInfo] = {
    val main = authcUi
    val loginPage: Directive1[UserInfo] = extract(_.request.uri).flatMap { uri ⇒
      val ref = uri.toRelative.resolvedAgainst(settings.baseUrl)
      val loginUrl = settings.uiUri("login").withQuery(Map("r" -> ref.toString)).toRelative
      Route.toDirective(redirect(loginUrl, StatusCodes.TemporaryRedirect))
    }
    main | loginPage
  }

  def checkAccessOrLogin(subject: String, authz: (PorterContext, RestContext) ⇒ Directive0): Directive1[UserInfo] = {
    authcUiOrLogin.flatMap { uinfo ⇒
      val rctx = RestContext(uinfo.name, uinfo.name, uinfo.token)
      authz(settings.porter, rctx).hflatMap {
        _ ⇒ provide(uinfo)
      }
    }
  }

  def send(successMsg: String, errorMsg: String)(f: Future[OperationFinished]): Route = {
    onSuccess(f) { r =>
      val msg: StringResult =
        if (r.success) Success(successMsg)
        else Failure(errorMsg)
      complete(msg)
    }
  }

  def getEntry(store: ActorRef, req: GetEntry): Directive1[PageEntry] = {
    val f = (store ? req).mapTo[Result[PageEntry]]
    onSuccess(f).flatMap {
      case Success(Some(e), _) => provide(e)
      case Success(None, _) => reject()
      case Failure(msg, error) => error.map(t => throw t) getOrElse (sys.error(msg))
    }
  }
}
