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

  def checkToken(subject: Ident, token: Token, authz: (PorterContext, RestContext) ⇒ Directive0): Directive1[RestContext] = {
    authenticateAccount(settings.tokenContext, Set(PasswordCredentials(subject, token.token))).flatMap { acc ⇒
      val rctx = RestContext(acc.name, subject, Some(token))
      authz(settings.tokenContext, rctx).hflatMap {
        _ ⇒ provide(rctx)
      }
    }
  }

  def checkAccess(subject: String, authz: (PorterContext, RestContext) ⇒ Directive0): Directive1[RestContext] = {
    val auth = authc(settings).map(acc ⇒ RestContext(acc.name, subject, UserInfo.token.get(acc.props)))
    auth.flatMap(ctx ⇒ authz(settings.porter, ctx).hflatMap(_ ⇒ provide(ctx)))
  }

  def authenticateWithCookie = authcWithCookie(settings)

  def removeAuthCookie = AuthDirectives.removeAuthCookie(settings)

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
    authz2(porter, rctx.authId, permission.createUser)

  def checkDeleteUser(porter: PorterContext, rctx: RestContext): Directive0 =
    authz2(porter, rctx.authId, permission.deleteUser(rctx.subject))

  def checkListTags(porter: PorterContext, rctx: RestContext): Directive0 =
    authz2(porter, rctx.authId, permission.listTags(rctx.subject))

  def checkGenerateToken(porter: PorterContext, rctx: RestContext): Directive0 =
    authz2(porter, rctx.authId, permission.generateToken(rctx.subject))

  def checkChangePassword(porter: PorterContext, rctx: RestContext): Directive0 =
    authz2(porter, rctx.authId, permission.changePassword(rctx.subject))

  def checkGetEntry(entryId: String)(porter: PorterContext, rctx: RestContext): Directive0 =
    authz2(porter, rctx.authId, permission.getEntry(rctx.subject, entryId))

  def checkGetEntries(porter: PorterContext, rctx: RestContext): Directive0 =
    authz2(porter, rctx.authId, permission.getEntries(rctx.subject))

  def checkAddEntry(porter: PorterContext, rctx: RestContext): Directive0 =
    authz2(porter, rctx.authId, permission.addEntry(rctx.subject))

  def checkDeleteEntry(entryId: String)(porter: PorterContext, rctx: RestContext): Directive0 =
    authz2(porter, rctx.authId, permission.deleteEntry(rctx.subject, entryId))

  def checkUpdateEntry(entryId: String)(porter: PorterContext, rctx: RestContext): Directive0 =
    authz2(porter, rctx.authId, permission.updateEntry(rctx.subject, entryId))

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
