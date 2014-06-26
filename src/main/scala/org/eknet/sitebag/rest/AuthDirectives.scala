package org.eknet.sitebag.rest

import org.eknet.sitebag.SitebagSettings
import porter.app.client.spray.{CookieSettings, PorterDirectives}
import porter.model._
import spray.http.DateTime
import spray.http.HttpCookie
import spray.http.StatusCodes
import spray.http.Uri
import spray.routing._
import spray.routing.AuthenticationFailedRejection.{CredentialsMissing, CredentialsRejected}
import scala.concurrent.ExecutionContext
import akka.util.Timeout
import porter.app.client.PorterContext
import porter.model.Account
import org.eknet.sitebag.model.Token
import spray.httpx.unmarshalling.FromRequestUnmarshaller

trait AuthDirectives extends PorterDirectives with Directives {
  private val cookieName = "SITEBAG_AUTH"

  def setAuthCookie(account: Account, settings: SitebagSettings): Directive0 = {
    val secure = settings.baseUrl.scheme == "https"
    account.secrets.find(_.name != Token.secretName) match {
      case Some(secret) =>
        setPorterCookie(account, CookieSettings(
          cookieKey = settings.cookieKey,
          cookiePath = settings.baseUrl.toRelative.toString,
          cookieName = cookieName,
          cookieSecure = secure), _ => secret)
      case _ => pass
    }
  }

  def removeAuthCookie(settings: SitebagSettings): Directive0 = {
    val secure = settings.baseUrl.scheme == "https"
    setCookie(HttpCookie(name = cookieName,
      content = "",
      path = Some(settings.baseUrl.toRelative.toString),
      maxAge = Some(0L),
      secure = secure,
      expires = None))
  }

  private def jsonCredentials[T <: PasswordCredentials](implicit jsonf: FromRequestUnmarshaller[T]): Directive1[Set[Credentials]] = {
    entity(as[T]).flatMap { up =>
      provide(Set[Credentials](up))
    }.recover(_ => provide(Set.empty))
  }

  def loginCredentials(cookieKey: Vector[Byte]): Directive1[Set[Credentials]] = {
    import JsonProtocol._
    import spray.httpx.SprayJsonSupport._
    jsonCredentials[UserPassCredentials] ++
      formCredentials("username", "password") ++
      cookieCredentials(cookieKey, cookieName)
  }

  private def usePorterClasses(creds: Set[Credentials]): Set[Credentials] = creds map {
    case pc: PasswordCredentials => PasswordCredentials(pc.accountName, pc.password)
    case x => x
  }
  def authc(settings: SitebagSettings)(implicit ec: ExecutionContext, to: Timeout): Directive1[Account] = {
    val main = loginCredentials(settings.cookieKey).flatMap { creds ⇒
      authenticateAccount(settings.porter, usePorterClasses(creds))
    }
    val fail: Directive1[Account] = loginCredentials(settings.cookieKey).flatMap { creds ⇒
      if (creds.isEmpty) Route.toDirective(reject(AuthenticationFailedRejection(CredentialsMissing, Nil)))
      else Route.toDirective(reject(AuthenticationFailedRejection(CredentialsRejected, Nil)))
    }
    (main | fail)
  }

  def authcWithCookie(settings: SitebagSettings)(implicit ec: ExecutionContext, to: Timeout): Directive1[Account] = {
    authc(settings).flatMap { acc ⇒
      setAuthCookie(acc, settings) & provide(acc)
    }
  }
}
object AuthDirectives extends AuthDirectives
