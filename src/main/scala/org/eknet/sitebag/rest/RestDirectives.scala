package org.eknet.sitebag.rest

import spray.routing._
import org.eknet.sitebag._
import scala.concurrent.ExecutionContext
import akka.util.Timeout
import porter.model.{PasswordCredentials, Account, Ident}
import spray.routing.directives.LogEntry
import spray.http._
import akka.event.Logging
import akka.actor.ActorRef
import akka.pattern.ask
import spray.http.HttpResponse
import porter.app.client.PorterContext
import org.eknet.sitebag.content.Content
import org.eknet.sitebag.GetEntryContent
import org.eknet.sitebag.model.{Token, UserInfo}
import spray.util.LoggingContext

trait RestDirectives extends Directives with CommonDirectives {

  import AuthDirectives._

  def settings: SitebagSettings
  implicit def executionContext: ExecutionContext
  implicit def timeout: Timeout

  def authenticateToken(subject: Ident, token: Token, rules: Set[String]): Directive1[RestContext] = {
    authenticateAccount(settings.tokenContext, Set(PasswordCredentials(subject, token.token))).flatMap {
      acc => authz2(settings.tokenContext, acc.name, rules).hflatMap {
        _ => provide(RestContext(acc.name, subject, Some(token)))
      }
    }
  }
  def authenticate(implicit _p: PorterContext = settings.porter): Directive1[Account] =
    authc(settings.cookieKey)

  def withAccount(subject: String)(implicit _p: PorterContext = settings.porter): Directive1[RestContext] = {
    authc(settings.cookieKey).map(acc => RestContext(acc.name, subject, UserInfo.token.get(acc.props)))
  }

  def checkAccess(subject: String, porter: PorterContext, authz: (PorterContext, RestContext) => Directive0): Directive1[RestContext] = {
    val auth = withAccount(subject)(porter)
    auth.flatMap(ctx => authz(porter, ctx).hflatMap(_ => provide(ctx)))
  }

  private def accessDeniedLogMessage(authId: Ident, perms: Set[String])(any: Any) =
    LogEntry(s"User '${authId.name}' denied '${perms.mkString(", ")}'", Logging.ErrorLevel)

  def authz2(porter: PorterContext, authId: Ident, perms: Set[String]): Directive0 = {
    val log = settings.logger(getClass)
    implicit val loggingContext = LoggingContext.fromAdapter(log)
    authz(porter, authId, perms).recover  { rejections =>
      logResponse(accessDeniedLogMessage(authId, perms) _) & reject(rejections: _*)
    }
  }

  def hasPerm(porter: PorterContext, authId: Ident, perms: Set[String]): Directive1[Boolean] = {
    onSuccess(porter.authorize(authId, perms).map(_.authorized))
  }

  def checkCreateUser(porter: PorterContext, rctx: RestContext): Directive0 =
    authz2(porter, rctx.authId, Set("sitebag:createuser"))

  def checkListTags(porter: PorterContext, rctx: RestContext): Directive0 =
    authz2(porter, rctx.authId, Set(s"sitebag:${rctx.subject.name}:listtags"))

  def checkGenerateToken(porter: PorterContext, rctx: RestContext): Directive0 =
    authz2(porter, rctx.authId, Set(s"sitebag:${rctx.subject.name}:generatetoken"))

  def checkChangePassword(porter: PorterContext, rctx: RestContext): Directive0 =
    authz2(porter, rctx.authId, Set(s"sitebag:${rctx.subject.name}:changepassword"))

  def checkGetEntry(entryId: String)(porter: PorterContext, rctx: RestContext): Directive0 =
    authz2(porter, rctx.authId, Set(s"sitebag:${rctx.subject.name}:entry:get:$entryId"))

  def checkGetEntries(porter: PorterContext, rctx: RestContext): Directive0 =
    authz2(porter, rctx.authId, Set(s"sitebag:${rctx.subject.name}:entry:get"))

  def checkAddEntry(porter: PorterContext, rctx: RestContext): Directive0 =
    authz2(porter, rctx.authId, Set(s"sitebag:${rctx.subject.name}:entry:add"))

  def checkDeleteEntry(entryId: String)(porter: PorterContext, rctx: RestContext): Directive0 =
    authz2(porter, rctx.authId, Set(s"sitebag:${rctx.subject.name}:entry:delete:$entryId"))

  def checkUpdateEntry(entryId: String)(porter: PorterContext, rctx: RestContext): Directive0 =
    authz2(porter, rctx.authId, Set(s"sitebag:${rctx.subject.name}:entry:update:$entryId"))

  def getEntryContent(store: ActorRef, req: GetEntryContent): Route = {
    val f = (store ? req).mapTo[Result[Content]]
    onSuccess(f) {
      case Success(Some(c), _) =>
        val ct = c.contentType.getOrElse(ContentTypes.`application/octet-stream`)
        complete(HttpResponse(status = StatusCodes.OK, entity = HttpEntity(ct, c.data)))
      case x =>
        complete(HttpResponse(status = StatusCodes.NotFound))
    }
  }
}
