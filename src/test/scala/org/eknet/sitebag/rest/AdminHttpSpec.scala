package org.eknet.sitebag.rest

import spray.testkit.Specs2RouteTest
import spray.routing.{Route, AuthorizationFailedRejection, HttpService}
import akka.util.Timeout
import java.util.concurrent.TimeUnit
import org.eknet.sitebag._
import spray.httpx.SprayJsonSupport
import spray.http.HttpHeaders._
import spray.http.{StatusCodes, BasicHttpCredentials}
import org.eknet.sitebag.{AdminActor, SitebagSettings}
import scala.concurrent.duration.FiniteDuration
import org.specs2.mutable.Specification
import porter.app.client.spray.PorterDirectives
import scala.concurrent.Await
import org.eknet.sitebag.mongo.ReextractActor

class AdminHttpSpec extends Specification with Specs2RouteTest with HttpService with FormDataSerialize {
  def actorRefFactory = system
  implicit val timeout = Timeout(3000, TimeUnit.MILLISECONDS)
  import JsonProtocol._
  import SprayJsonSupport._

  private val settings = SitebagSettings(system)
  private val storeActor = system.actorOf(DummyStoreActor())
  val extractor = system.actorOf(ExtractionActor(), "extractors")
  val reextractor = system.actorOf(ReextractActor(extractor))
  private val adminActor = system.actorOf(AdminActor(storeActor, reextractor))
  implicit val routeTo = RouteTestTimeout(FiniteDuration(10, TimeUnit.SECONDS))

  def route(subject: String) =
    new AdminHttp(settings, adminActor, actorRefFactory.dispatcher, timeout).route(subject, settings.porter)

  def as(username: String, password: String = "test") = addCredentials(BasicHttpCredentials(username, password))
  def asNobody = as("nobody")
  def asAdmin = as("admin")
  def asSuperuser = as("superuser")

  action {
    Await.ready(settings.mongoClient.db.drop(), timeout.duration)
  }

  "The admin http service" should {
    "create new user with success for admins" in {
      Put("/", NewPassword("superword")) ~> asSuperuser ~> route("textx12") ~> check {
        println(responseAs[String])
        status === StatusCodes.OK
        responseAs[Ack].isSuccess === true
      }
      Put("/", NewPassword("superword").toFormData) ~> asSuperuser ~> route("testx13") ~> check {
        status === StatusCodes.OK
        responseAs[Ack].isSuccess === true
      }
    }

    "generate new tokens for existing user" in {
      Post("/newtoken") ~> asSuperuser ~> route("admin") ~> check {
        status === StatusCodes.OK
        responseAs[StringResult] match {
          case Success(Some(token), _) => assert(token.length > 0)
          case x => sys.error("Invalid response: " + x)
        }
        true
      }
    }
    "deny generating tokens for unauthorized user" in {
      Post("/newtoken") ~> asNobody ~> route("admin") ~> check {
        rejection === AuthorizationFailedRejection
      }
    }

    "be able to change own password" in {
      Put("/", NewPassword("superword")) ~> asSuperuser ~> route("test55") ~> check {
        status === StatusCodes.OK
        responseAs[Ack].isSuccess === true
      }
      Post("/changepassword", NewPassword("xyz123")) ~> asSuperuser ~> route("test55") ~> check {
        status === StatusCodes.OK
        responseAs[Ack] match {
          case Success(_, _) => true
          case Failure(msg, error) =>
            error.map(x => throw x).getOrElse(sys.error(msg))
        }
      }
    }
  }

}
