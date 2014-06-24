package org.eknet.sitebag.rest

import porter.model.Ident
import scala.concurrent._
import spray.testkit.Specs2RouteTest
import spray.routing.{AuthorizationFailedRejection, HttpService}
import akka.util.Timeout
import java.util.concurrent.TimeUnit
import org.eknet.sitebag._
import spray.httpx.SprayJsonSupport
import spray.http.{StatusCodes, BasicHttpCredentials}
import org.eknet.sitebag.{AdminActor, SitebagSettings}
import scala.concurrent.duration.FiniteDuration
import org.specs2.mutable.Specification
import org.eknet.sitebag.mongo.SitebagMongo

class AdminHttpSpec extends Specification with Specs2RouteTest with HttpService with FormDataSerialize {
  sequential

  def actorRefFactory = system
  implicit val timeout = Timeout(3000, TimeUnit.MILLISECONDS)
  import JsonProtocol._
  import SprayJsonSupport._
  import commons.withUser

  private val settings = SitebagSettings(system)
  private val mongo = SitebagMongo(settings)
  private val adminActor = system.actorOf(AdminActor(null, settings.porter, mongo, settings))
  implicit val routeTo = RouteTestTimeout(FiniteDuration(10, TimeUnit.SECONDS))

  def dropDb() = {
    Await.ready(mongo.db.drop(), timeout.duration)
    true
  }

  def route(subject: String) =
    new AdminHttp(settings, adminActor, actorRefFactory.dispatcher, timeout).route(subject, settings.porter)

  def as(username: Ident, password: String = "test") = Map("username" → username.name, "password" → password)
  def asSuperuser = as(PorterStore.superuser.name, PorterStore.password)

  def asNobody = as("nobody")
  def asAdmin = as("admin")

  "The admin http service" should {
    doBefore { dropDb() }

    "create new user with success for admins" in {
      val name1 = commons.randomWord
      val name2 = commons.randomWord
      Put("/", withUser("admin", "test", NewPassword("superword"))) ~> route(name1) ~> check {
        println(responseAs[String])
        status === StatusCodes.OK
        responseAs[Ack].isSuccess === true
      }
      Put("/", NewPassword("superword").toFormData.as("admin", "test")) ~> route(name2) ~> check {
        status === StatusCodes.OK
        responseAs[Ack].isSuccess === true
      }
    }

    "generate new tokens for existing user" in {
      Post("/newtoken", asSuperuser) ~> route("superuser") ~> check {
        status === StatusCodes.OK
        responseAs[StringResult] match {
          case Success(Some(token), _) => assert(token.length > 0)
          case x => sys.error("Invalid response: " + x)
        }
        true
      }
    }
    "deny generating tokens for unauthorized user" in {
      Post("/newtoken", asNobody) ~> route("superuser") ~> check {
        rejection === AuthorizationFailedRejection
      }
    }

    "be able to change own password" in {
      Post("/changepassword", Map("username" → "mary", "password" → "test", "newpassword" → "xyz123")) ~> route("mary") ~> check {
        status === StatusCodes.OK
        response.headers.count(_ is "set-cookie") === 1
        responseAs[Ack] match {
          case Success(_, _) => true
          case Failure(msg, error) =>
            error.map(x => throw x).getOrElse(sys.error(msg))
        }
      }
    }
    "delete accounts" in {
      def checkResponse = {
        status === StatusCodes.OK
        responseAs[Ack] match {
          case Success(_, msg) => assert(msg === "Account removed."); true
          case Failure(msg, error) =>
            error.map(x => throw x).getOrElse(sys.error(msg))
        }
      }
      def checkDeleteMaryAs(name: String) = {
        Post("/", withUser(name, "test", DeleteAction(true))) ~> route("mary") ~> check {
          checkResponse
        }
        Post("/", DeleteAction(true).toFormData.as(name, "test")) ~> route("mary") ~> check {
          checkResponse
        }
        Delete("/", as(name)) ~> route("mary") ~> check {
          checkResponse
        }
      }
      checkDeleteMaryAs("mary")
      checkDeleteMaryAs("superuser")
    }
  }

}
