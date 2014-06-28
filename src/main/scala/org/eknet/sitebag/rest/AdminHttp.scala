package org.eknet.sitebag.rest

import scala.concurrent.ExecutionContext
import akka.util.Timeout
import akka.pattern.ask
import spray.routing._
import spray.httpx.SprayJsonSupport
import org.eknet.sitebag._
import org.eknet.sitebag.model._
import porter.model._
import org.eknet.sitebag.SitebagSettings
import akka.actor.ActorRef
import porter.client.PorterClient
import porter.app.client.PorterContext
import spray.json.RootJsonFormat
import spray.httpx.marshalling.ToResponseMarshaller

class AdminHttp(val settings: SitebagSettings, adminRef: ActorRef, ec: ExecutionContext, to: Timeout)
  extends Directives with RestDirectives with FormUnmarshaller {

  def porter = settings.porter
  implicit def timeout = to
  implicit def executionContext = ec

  import JsonProtocol._
  import SprayJsonSupport._

  def route(subject: String, porter: PorterContext): Route = {
    pathEndOrSingleSlash {
      deleteRequest {
        checkAccess(subject, checkDeleteUser) { rctx =>
          complete {
            (adminRef ? DeleteUser(rctx.subject)).mapTo[Ack]
          }
        }
      } ~
      (put | post) {
        checkAccess(subject, checkCreateUser) { rctx =>
          handle { np: NewPassword =>
            (adminRef ? CreateUser(rctx.subject, np.newpassword)).mapTo[Ack]
          }
        }
      }
    } ~
    post {
      path("newtoken") {
        checkAccess(subject, checkGenerateToken) { rctx =>
          complete {
            (adminRef ? GenerateToken(rctx.subject)).mapTo[StringResult]
          }
        }
      } ~
      path("changepassword") {
        checkAccess(subject, checkChangePassword) { rctx =>
          removeAuthCookie {
            handle { np: NewPassword =>
              (adminRef ? ChangePassword(rctx.subject, np.newpassword)).mapTo[Ack]
            }
          }
        }
      } ~
      path("reextract") {
        checkAccess(subject, checkAddEntry) { rctx =>
          handle { re: ReextractAction ⇒
            (adminRef ? ReExtractContent(rctx.subject, re.entryId)).mapTo[Ack]
          }
        }
      }
    } ~
    get {
      path("reextract") {
        parameter('status) { _ ⇒
          checkAccess(subject, checkAddEntry) { rctx =>
            complete( (adminRef ? ReExtractStatusRequest(rctx.subject)).mapTo[Result[ReExtractStatus]] )
          }
        }
      }
    }
  }

  def deleteRequest: Directive0 = {
    implicit val u =  unm(deleteActionFormat, deleteActionFormData)
    def asParam: Directive0 = entity(as[DeleteAction]).flatMap {
      case DeleteAction(true) => pass
      case _                  => reject
    }
    delete | post.hflatMap(_ => asParam)
  }
}
