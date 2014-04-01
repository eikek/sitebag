package org.eknet.sitebag.rest

import scala.concurrent.ExecutionContext
import akka.util.Timeout
import akka.pattern.ask
import spray.routing._
import spray.httpx.SprayJsonSupport
import org.eknet.sitebag.model._
import porter.model._
import org.eknet.sitebag.SitebagSettings
import akka.actor.ActorRef

class AdminHttp(settings: SitebagSettings, adminRef: ActorRef, ec: ExecutionContext, to: Timeout) extends Directives with AuthDirectives with CommonDirectives {

  def porter = settings.porter
  implicit def timeout = to
  implicit def executionContext = ec

  import JsonProtocol._
  import SprayJsonSupport._

  def route: Route = {
    basicCredentials.isEmpty {
      sendChallenge(AuthenticationFailedRejection.CredentialsMissing)
    } ~
    authBasic { auth =>
      (path("api" / "newuser") & post) {
        checkCreateUser(auth.accountId) {
          handle { cu: CreateUser =>
            (adminRef ? cu).mapTo[Result]
          }
        }
      } ~
      path("api" / Segment / "newtoken") { account =>
        checkGenerateToken(Ident(account), auth.accountId) {
          complete {
            (adminRef ? GenerateToken(account)).mapTo[TokenResult]
          }
        }
      } ~
      path("api" / Segment / "changepassword") { account =>
        checkChangePassword(Ident(account), auth.accountId) {
          handle { np: NewPassword =>
            (adminRef ? ChangePassword(account, np.password)).mapTo[Result]
          }
        }
      }
    }
  }
}
