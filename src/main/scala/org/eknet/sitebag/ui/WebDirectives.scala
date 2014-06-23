package org.eknet.sitebag.ui

import scala.concurrent.Future
import spray.routing.{Directives, Route, Directive1}
import akka.pattern.ask
import porter.client.messages.OperationFinished
import org.eknet.sitebag.rest.RestDirectives
import org.eknet.sitebag.model.{PageEntry, Token, UserInfo}
import org.eknet.sitebag._
import akka.actor.ActorRef
import org.eknet.sitebag.content.Content
import spray.http.{HttpEntity, StatusCodes, HttpResponse, ContentTypes}
import twirl.api.Html

trait WebDirectives extends Directives {
  self: RestDirectives  =>

  import org.eknet.sitebag.rest.JsonProtocol._
  import spray.httpx.SprayJsonSupport._
  import TwirlSupport._

  def webSettings: WebSettings

  def render(info: UserInfo, title: String, body: Html): Route = {
    complete {
      html.layout(info, webSettings, title, body)
    }
  }

  def authc: Directive1[UserInfo] = {
    for {
      acc <- authenticate(settings.porter)
      canCreate <- hasPerm(settings.porter, acc.name, Set("sitebag:createuser"))
    } yield {
      val token = UserInfo.token.get(acc.props)
      UserInfo(acc.name.name, acc.props, token, canCreate)
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
