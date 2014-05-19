package org.eknet.sitebag.rest

import scala.concurrent.{Await, Future}
import org.specs2.mutable.Specification
import spray.testkit.Specs2RouteTest
import spray.routing.{AuthenticationFailedRejection, Route, HttpService}
import akka.util.Timeout
import porter.model.{Group, Password, Account}
import org.eknet.sitebag.SitebagSettings
import org.eknet.sitebag.model.Token
import java.util.concurrent.TimeUnit
import spray.httpx.SprayJsonSupport
import spray.http.{StatusCodes, HttpHeaders, BasicHttpCredentials, FormData}
import HttpHeaders._
import porter.app.client.spray.PorterDirectives
import scala.concurrent.duration.FiniteDuration

class AuthRouteSpec extends Specification with Specs2RouteTest with HttpService with FormDataSerialize with RestDirectives {
  sequential

  def actorRefFactory = system
  implicit val timeout: Timeout = Timeout(3, TimeUnit.SECONDS)
  implicit val timeoutDur = timeout.duration
  import JsonProtocol._

  case class Payload(username: String, password: String, somevalue: String)
  case class PayloadToken(username: String, token: String, somevalue: String)
  implicit val payloadFormat = jsonFormat3(Payload)
  implicit val payloadTokenFormat = jsonFormat3(PayloadToken)
  implicit val routeTo = RouteTestTimeout(FiniteDuration(10, TimeUnit.SECONDS))

  import SprayJsonSupport._
  val executionContext = system.dispatcher
  val settings = SitebagSettings(system)
  val porter = settings.porter

  private def mainRoute(subject: String): Route = withAccount(subject)(settings.porter) { ctx =>
    complete(ctx.authId.name +":"+ ctx.subject.name)
  }
  private def tokenRoute(subject: String): Route = withAccount(subject)(settings.tokenContext) { ctx =>
    complete(ctx.authId.name +":"+ ctx.subject.name)
  }

  def as(username: String, password: String = "test") = addCredentials(BasicHttpCredentials(username, password))

  "The withAccount directive" should {
    "allow to authenticate with json params" in {
      Post("/", Payload("superuser2", "test", "apple")) ~> mainRoute("johnny") ~> check {
        responseAs[String] === "superuser2:johnny"
        response.headers.count(_ is "set-cookie") === 1
      }
    }
    "allow to authenticate via formdata" in {
      Post("/", FormData(Map("username" -> "superuser2", "password" -> "test", "other" -> "bla"))) ~> mainRoute("johnny") ~> check {
        responseAs[String] === "superuser2:johnny"
        response.headers.count(_ is "set-cookie") === 1
      }
    }
    "allow to authenticate via http-basic" in {
      Post("/") ~> as("admin") ~> mainRoute("johnny") ~> check {
        responseAs[String] === "admin:johnny"
        response.headers.count(_ is "set-cookie") === 0 //admin user has no token secret
      }
    }
    "allow to authenticate via cookies" in {
      var cookies: Seq[`Set-Cookie`] = Nil
      Post("/") ~> as("superuser2") ~> mainRoute("johnny") ~> check {
        responseAs[String] === "superuser2:johnny"
        cookies = response.headers.collect { case sc: `Set-Cookie` => sc }
        cookies.size === 1
      }
      Post("/") ~> addHeader(Cookie(cookies.map(_.cookie))) ~> tokenRoute("johnny") ~> check {
        responseAs[String] === "superuser2:johnny"
        response.headers.count(_ is "set-cookie") === 0
      }
    }
    "reject tokens from main route" in {
      Post("/", PayloadToken("superuser2", "abc", "bla")) ~> mainRoute("johnny") ~> check {
        rejection === PorterDirectives.httpBasicChallenge(AuthenticationFailedRejection.CredentialsRejected)
      }
    }
    "reject passwords from token route" in {
      Post("/") ~> as("admin", "test") ~> tokenRoute("johnny") ~> check {
        rejection === PorterDirectives.httpBasicChallenge(AuthenticationFailedRejection.CredentialsRejected)
      }
    }
    "allow to authenticate with a 'token' in tokenRoute" in {
      Post("/", PayloadToken("superuser2", "abc", "blabla")) ~> tokenRoute("johnny") ~> check {
        responseAs[String] === "superuser2:johnny"
        response.headers.count(_ is "set-cookie") === 1
      }
      Post("/", FormData(Map("username" -> "superuser2", "token" -> "abc", "other" -> "bla"))) ~> tokenRoute("johnny") ~> check {
        responseAs[String] === "superuser2:johnny"
        response.headers.count(_ is "set-cookie") === 1
      }
    }
    "allow to authenticate via token cookies" in {
      var cookies: Seq[`Set-Cookie`] = Nil
      Post("/", PayloadToken("superuser2", "abc", "blup")) ~> tokenRoute("johnny") ~> check {
        cookies = response.headers.collect { case sc: `Set-Cookie` => sc }
        status === StatusCodes.OK
      }
      assert(cookies.size === 1)
      Post("/") ~> addHeader(Cookie(cookies.map(_.cookie))) ~> tokenRoute("johnny") ~> check {
        responseAs[String] === "superuser2:johnny"
        response.headers.count(_ is "set-cookie") === 0
      }
      Post("/") ~> addHeader(Cookie(cookies.map(_.cookie))) ~> mainRoute("johnny") ~> check {
        rejection === PorterDirectives.httpBasicChallenge(AuthenticationFailedRejection.CredentialsRejected)
      }
    }
    "deny to authenticate via token cookie at main route" in {
      var cookies: Seq[`Set-Cookie`] = Nil
      Post("/", PayloadToken("superuser2", "abc", "blup")) ~> tokenRoute("johnny") ~> check {
        cookies = response.headers.collect { case sc: `Set-Cookie` => sc }
        status === StatusCodes.OK
      }
      Post("/") ~> addHeader(Cookie(cookies.map(_.cookie))) ~> mainRoute("johnny") ~> check {
        rejection === PorterDirectives.httpBasicChallenge(AuthenticationFailedRejection.CredentialsRejected)
      }
    }
  }

}
