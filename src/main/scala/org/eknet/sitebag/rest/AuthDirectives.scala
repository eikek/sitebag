package org.eknet.sitebag.rest

import porter.app.client.spray.{CookieSettings, PorterDirectives}
import porter.model._
import spray.routing._
import scala.concurrent.ExecutionContext
import akka.util.Timeout
import porter.app.client.PorterContext
import porter.model.Account
import org.eknet.sitebag.model.Token
import spray.httpx.unmarshalling.FromRequestUnmarshaller

trait AuthDirectives extends PorterDirectives with Directives {
  private val cookieName1 = "SITEBAG_AUTH"
  private val cookieName2 = "SITEBAG_TOKEN"

  def setAuthCookies(account: Account, key: Vector[Byte]): Directive0 =
    setMainCookie(account, key) & setTokenCookie(account, key)

  def setTokenCookie(account: Account, key: Vector[Byte]): Directive0 = {
    account.secrets.find(_.name == Token.secretName) match {
      case Some(secret) =>
        setPorterCookie(account, CookieSettings(cookieKey = key, cookieName = cookieName2, cookieSecure = false), _ => secret)
      case _ => pass
    }
  }

  def setMainCookie(account: Account, key: Vector[Byte]): Directive0 = {
    account.secrets.find(_.name.name startsWith "password.") match {
      case Some(secret) =>
        setPorterCookie(account, CookieSettings(cookieKey = key, cookieName = cookieName1, cookieSecure = false), _ => secret)
      case _ => pass
    }
  }

  private def jsonCredentials[T <: PasswordCredentials](implicit jsonf: FromRequestUnmarshaller[T]): Directive1[Set[Credentials]] = {
    entity(as[T]).flatMap { up =>
      provide(Set[Credentials](up))
    }.recover(_ => provide(Set.empty))
  }

  private def toTokenCredentials(set: Set[Credentials]): Set[Credentials] =
    set.collect({ case pc: PasswordCredentials => TokenCredentials(pc.accountName.name, pc.password) })

  def loginCredentials: Directive1[Set[Credentials]] = {
    import JsonProtocol._
    import spray.httpx.SprayJsonSupport._
    basicCredentials.map(s => s ++ toTokenCredentials(s)) ++
      jsonCredentials[UserPassCredentials] ++
      jsonCredentials[TokenCredentials] ++
      formCredentials("username", "password") ++
      formCredentials("username", "token").map(toTokenCredentials)
  }

  def derivedCredentials(cookieKey: Vector[Byte]): Directive1[Set[Credentials]] = {
    cookieCredentials(cookieKey, cookieName1) ++ cookieCredentials(cookieKey, cookieName2)
  }

  private def usePorterClasses(creds: Set[Credentials]): Set[Credentials] = creds map {
    case pc: PasswordCredentials => PasswordCredentials(pc.accountName, pc.password)
    case x => x
  }
  def authc(key: Vector[Byte])(implicit porter: PorterContext, ec: ExecutionContext, to: Timeout): Directive1[Account] = {
    val main = loginCredentials.flatMap { creds =>
      authenticateAccount(porter, usePorterClasses(creds)).flatMap { acc =>
        setTokenCookie(acc, key) & provide(acc)
      }
    }
    val second = derivedCredentials(key).flatMap { creds =>
      authenticateAccount(porter, creds)
    }
    val fail: Directive1[Account] = (loginCredentials ++ derivedCredentials(key)).flatMap { creds =>
      if (creds.isEmpty) Route.toDirective(sendChallenge(AuthenticationFailedRejection.CredentialsMissing))
      else Route.toDirective(sendChallenge())
    }
    (main | second) | fail
  }


}
object AuthDirectives extends AuthDirectives