package org.eknet.sitebag.rest

import spray.testkit.Specs2RouteTest
import spray.routing.{AuthorizationFailedRejection, AuthenticationFailedRejection, HttpService}
import akka.util.Timeout
import java.util.concurrent.TimeUnit
import org.eknet.sitebag.model.{Token, Result, CreateUser}
import spray.httpx.SprayJsonSupport
import spray.http.HttpHeaders._
import spray.http.{StatusCodes, BasicHttpCredentials, HttpChallenge}
import org.eknet.sitebag.{MongoStoreActor, AdminActor, SitebagSettings}
import scala.concurrent.duration.FiniteDuration
import org.specs2.mutable.Specification

class AdminHttpSpec extends Specification with Specs2RouteTest with HttpService with FormDataSerialize {
  def actorRefFactory = system
  implicit val timeout = Timeout(3000, TimeUnit.MILLISECONDS)
  import JsonProtocol._
  import SprayJsonSupport._

  private val settings = SitebagSettings(system)
  private val storeActor = system.actorOf(MongoStoreActor(settings.mongoClient, settings.dbName))
  private val adminActor = system.actorOf(AdminActor(storeActor))
  private val route = new AdminHttp(settings, adminActor, actorRefFactory.dispatcher, timeout).route
  implicit val routeTo = RouteTestTimeout(FiniteDuration(5, TimeUnit.SECONDS))

  def as(username: String, password: String = "test") = addCredentials(BasicHttpCredentials(username, password))
  def asNobody = as("nobody")
  def asAdmin = as("admin")

  step {
    settings.mongoClient(settings.dbName).dropDatabase()
  }

  "The admin http service" should {
    "send basic challenge when creating new user" in {
      val cu = CreateUser("testx", "superword")
      Post("/api/newuser", cu) ~> route ~> check {
        rejection === AuthenticationFailedRejection(AuthenticationFailedRejection.CredentialsMissing, List(`WWW-Authenticate`(HttpChallenge("Basic", "Protected Area"))))
      }
      Post("/api/newuser", cu.toFormData) ~> route ~> check {
        rejection === AuthenticationFailedRejection(AuthenticationFailedRejection.CredentialsMissing, List(`WWW-Authenticate`(HttpChallenge("Basic", "Protected Area"))))
      }
    }
    "reject unauthorized when creating a new user" in {
      Post("/api/newuser", CreateUser("testx1", "superword")) ~> asNobody ~> route ~> check {
        rejection === AuthorizationFailedRejection
      }
      Post("/api/newuser", CreateUser("testx1", "superword").toFormData) ~> asNobody ~> route ~> check {
        rejection === AuthorizationFailedRejection
      }
    }
    "create new user with success for admins" in {
      Post("/api/newuser", CreateUser("testx12", "superword")) ~> asAdmin ~> route ~> check {
        println(responseAs[String])
        status === StatusCodes.OK
        responseAs[Result].success === true
      }
      Post("/api/newuser", CreateUser("testx13", "superword").toFormData) ~> asAdmin ~> route ~> check {
        status === StatusCodes.OK
        responseAs[Result].success === true
      }
    }

    "generate new tokens for existing user" in {
      val user = CreateUser("test14", "test")
      Post("/api/newuser", user) ~> asAdmin ~> route ~> check {
        status === StatusCodes.OK
      }
      Post("/api/test14/newtoken") ~> as("test14") ~> route ~> check {
        status === StatusCodes.OK
        responseAs[Token].token.length > 0
      }
    }
    "deny generating tokens for unauthorized user" in {
      val user = CreateUser("test15", "test")
      Post("/api/newuser", user) ~> asAdmin ~> route ~> check {
        status === StatusCodes.OK
      }
      Post("/api/nobody/newtoken") ~> as("test15") ~> route ~> check {
        rejection === AuthorizationFailedRejection
      }
    }

    "be able to change own password" in {
      val user = CreateUser("test16", "test")
      Post("/api/newuser", user) ~> asAdmin ~> route ~> check {
        status === StatusCodes.OK
      }
      Post("/api/test16/changepassword", NewPassword("xyz123")) ~> as("test16") ~> route ~> check {
        status === StatusCodes.OK
        responseAs[Result].success === true
      }
      Post("/api/test16/changepassword", NewPassword("test16").toFormData) ~> as("test16", "xyz123") ~> route ~> check {
        status === StatusCodes.OK
        responseAs[Result].success === true
      }
    }
  }

}
