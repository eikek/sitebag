package org.eknet.sitebag.rest

import scala.concurrent.duration._
import org.specs2.mutable.Specification
import akka.util.Timeout
import java.util.concurrent.TimeUnit
import spray.json.JsObject
import spray.json.JsonFormat
import spray.routing.{RequestContext, HttpService}
import spray.httpx.{TransformerAux, SprayJsonSupport}
import spray.http._
import spray.http.HttpHeaders._
import spray.testkit.Specs2RouteTest
import porter.model.Ident
import org.eknet.sitebag._
import org.eknet.sitebag.model.{Tag, PageEntry}
import org.eknet.sitebag.search.SearchActor

class AppHttpSpec extends Specification with Specs2RouteTest with HttpService with FormDataSerialize {
  sequential

  def actorRefFactory = system
  implicit val timeout = Timeout(3000, TimeUnit.MILLISECONDS)
  implicit val routeTo = RouteTestTimeout(FiniteDuration(10, TimeUnit.SECONDS))
  import JsonProtocol._
  import SprayJsonSupport._
  import commons._

  private val extrRef = system.actorOf(ExtractionActor())
  private val settings = SitebagSettings(system)
  private val storeActor = system.actorOf(DummyStoreActor())
  private val clientActor = createClient(extrRef, HttpResponse(entity = HttpEntity(htmlType, "<html>Hello world</html>")))
  private val appRef = system.actorOf(AppActor(clientActor, storeActor, null, settings))
  private def route(subject: String) =
    new AppHttp(settings, appRef, system, system.dispatcher, timeout).route(subject)

  val entry = DummyStoreActor.existingEntry

  def as(username: Ident, password: String = "test") = Map("username" → username.name, "password" → password)
  def asSuperuser = as(PorterStore.superuser.name, PorterStore.password)

  def assertAck = {
    responseAs[Ack] match {
      case Success(_, _) =>
      case x => sys.error("Invalid response: "+ x)
    }
    true
  }

  "The app http service" should {
    "login by sending a session cookie" in {
      Post("/login", UserPassCredentials("mary", "test")) ~> route("mary") ~> check {
        status === StatusCodes.OK
        response.header[`Set-Cookie`] match {
          case Some(`Set-Cookie`(cookie)) ⇒
            assert(cookie.content.nonEmpty)
            assert(cookie.maxAge == None)
            assert(cookie.expires == None)
          case _ ⇒ sys.error("no cookie")
        }
        responseAs[StringResult] match {
          case Success(Some(value), _ ) => assert(value === "mary")
          case x => sys.error("Invalid response: "+ x)
        }
        true
      }
    }
    "logout by sending a zero age cookie" in {
      Post("/logout", UserPassCredentials("mary", "test")) ~> route("mary") ~> check {
        status === StatusCodes.OK
        response.header[`Set-Cookie`] match {
          case Some(`Set-Cookie`(cookie)) ⇒
            assert(cookie.content.isEmpty)
            assert(cookie.maxAge.get == 0)
          case _ ⇒
            sys.error("no cookie")
        }
        assertAck
        true
      }
    }
    "add a page entry" in {
      val data = withUser("mary", "test", RAdd("https://dummy.org/test.html", None, Set.empty))
      Put("/entry", data) ~> route("mary") ~> check {
        status === StatusCodes.OK
        responseAs[StringResult] match {
          case Success(Some(value), _ ) => assert(value.length > 0)
          case x => sys.error("Invalid response: "+ x)
        }
        true
      }
    }
    "get a page entry" in {
      Get("/entry/"+ entry.id+ "?token=abc") ~> route("mary") ~> check {
        responseAs[Result[PageEntry]] match {
          case Success(Some(e), _) =>
            assert(e.title === entry.title)
            assert(e.content === entry.content)
          case x => sys.error("Invalid response: "+ x)
        }
        true
      }
    }
    "delete a page entry" in {
      Post("/entry/" + entry.id, FormData(as("mary") ++ Seq("delete" → ""))) ~> route("mary") ~> check {
        responseAs[Ack].message === "Page removed."
        assertAck
      }
      Delete("/entry/"+ entry.id, as("mary")) ~> route("mary") ~> check {
        responseAs[Ack].message === "Page removed."
        assertAck
      }
    }
    "toggle and set archived status" in {
      Post(s"/entry/${entry.id}/togglearchived", as("mary")) ~> route("mary") ~> check {
        assertAck
      }
      Post(s"/entry/${entry.id}/setarchived", withUser("mary", "test", Flag(true))) ~> route("mary") ~> check {
        assertAck
      }
      Post(s"/entry/${entry.id}/setarchived", Flag(true).toFormData.as("mary", "test")) ~> route("mary") ~> check {
        assertAck
      }
    }
    "tag and untag entries" in {
      Post(s"/entry/${entry.id}/tag", withUser("mary", "test", TagInput(Set(Tag.favourite)))) ~> route("mary") ~> check {
        assertAck
      }
      Post(s"/entry/${entry.id}/untag", withUser("mary", "test", TagInput(Set(Tag.favourite)))) ~> route("mary") ~> check {
        assertAck
      }
    }
    "list tags" in {
      Get("/tags", withUser("mary", "test", TagFilter(".*"))) ~> route("mary") ~> check {
        responseAs[Result[TagList]] match {
          case Success(Some(list), _) => assert(list === DummyStoreActor.tagList)
          case x => sys.error("Invalid response: "+x)
        }
        true
      }
      Get("/tags", as("mary")) ~> route("mary") ~> check {
        responseAs[Result[TagList]] match {
          case Success(Some(list), _) => assert(list === DummyStoreActor.tagList)
          case x => sys.error("Invalid response: "+x)
        }
        true
      }
    }
  }
}
