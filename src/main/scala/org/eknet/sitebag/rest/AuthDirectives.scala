package org.eknet.sitebag.rest

import porter.app.client.spray.PorterDirectives
import porter.model.{PasswordCredentials, Ident, Credentials}
import spray.routing._
import scala.concurrent.ExecutionContext
import akka.util.Timeout
import porter.auth.AuthResult
import porter.app.client.PorterContext

trait AuthDirectives extends PorterDirectives with Directives {

  def porter: PorterContext
  implicit def executionContext: ExecutionContext
  implicit def timeout: Timeout

  def authUser(msg: Credentials): Directive1[AuthResult] =
    authenticateResult(porter, Set(msg))


  def authBasic: Directive1[AuthResult] =
    basicCredentials.flatMap(creds => authenticateResult(porter, creds)).flatMap {
      case r: AuthResult => provide(r)
      case x =>
        reject(httpBasicChallenge(AuthenticationFailedRejection.CredentialsRejected))
    }

  def basicCredentialsOf(user: String): Directive1[Set[Credentials]] =
    basicCredentials.flatMap(_.toList.head match {
      case pc: PasswordCredentials if pc.accountName.name == user => provide(Set(pc))
      case _ => reject(httpBasicChallenge(AuthenticationFailedRejection.CredentialsRejected))
    })

  def checkCreateUser(account: Ident): Directive0 =
    authz(porter, account, Set("sitebag:createuser"))

  def checkGenerateToken(foraccount: Ident, account: Ident): Directive0 =
    authz(porter, account, Set(s"sitebag:${foraccount.name}:generatetoken"))

  def checkChangePassword(foraccount: Ident, account: Ident): Directive0 =
    authz(porter, account, Set(s"sitebag:${foraccount.name}:changepassword"))
}
