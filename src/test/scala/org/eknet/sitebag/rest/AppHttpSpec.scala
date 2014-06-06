package org.eknet.sitebag.rest

import scala.concurrent.duration._
import org.specs2.mutable.Specification
import akka.util.Timeout
import java.util.concurrent.TimeUnit
import spray.routing.{RequestContext, HttpService}
import spray.httpx.{TransformerAux, SprayJsonSupport}
import spray.http._
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

  def as(username: Ident, password: String = "test") = addCredentials(BasicHttpCredentials(username.name, password))
  def asSuperuser = as(PorterStore.superuser.name, PorterStore.password)

  def assertAck = {
    responseAs[Ack] match {
      case Success(_, _) =>
      case x => sys.error("Invalid response: "+ x)
    }
    true
  }

  "The app http service" should {
    "add a page entry" in {
      Put("/entry", RAdd("https://dummy.org/test.html", None, Set.empty)) ~> as("mary") ~> route("mary") ~> check {
        status === StatusCodes.OK
        responseAs[StringResult] match {
          case Success(Some(value), _ ) => assert(value.length > 0)
          case x => sys.error("Invalid response: "+ x)
        }
        true
      }
    }
    "get a page entry" in {
      Get("/entry/"+ entry.id) ~> as("mary", "abc") ~> route("mary") ~> check {
        responseAs[Result[PageEntry]] match {
          case Success(Some(e), _) =>
            assert(e.title === entry.title)
            assert(e.content === entry.content)
          case x => sys.error("Invalid response: "+ x)
        }
        true
      }
      Get("/entry?id="+DummyStoreActor.existingEntry.id) ~> as("mary", "abc") ~> route("mary") ~> check {
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
      Delete("/entry/"+ entry.id) ~> as("mary") ~> route("mary") ~> check {
        assertAck
      }
      Post("/entry/"+ entry.id, DeleteAction(true)) ~> as("mary") ~> route("mary") ~> check {
        responseAs[Ack].message === "Page removed."
        assertAck
      }
      Post("/entry/" + entry.id, FormData(Map("delete" -> ""))) ~> as("mary") ~> route("mary") ~> check {
        responseAs[Ack].message === "Page removed."
        assertAck
      }
    }
    "toggle and set archived status" in {
      Post(s"/entry/${entry.id}/togglearchived") ~> as("mary") ~> route("mary") ~> check {
        assertAck
      }
      Post(s"/entry/${entry.id}/setarchived", Flag(true)) ~> as("mary") ~> route("mary") ~> check {
        assertAck
      }
      Post(s"/entry/${entry.id}/setarchived", Flag(true).toFormData) ~> as("mary") ~> route("mary") ~> check {
        assertAck
      }
    }
    "tag and untag entries" in {
      Post(s"/entry/${entry.id}/tag", TagInput(Set(Tag.favourite))) ~> as("mary") ~> route("mary") ~> check {
        assertAck
      }
      Post(s"/entry/${entry.id}/untag", TagInput(Set(Tag.favourite))) ~> as("mary") ~> route("mary") ~> check {
        assertAck
      }
    }
    "list tags" in {
      Get("/tags", TagFilter(".*")) ~> as("mary") ~> route("mary") ~> check {
        responseAs[Result[TagList]] match {
          case Success(Some(list), _) => assert(list === DummyStoreActor.tagList)
          case x => sys.error("Invalid response: "+x)
        }
        true
      }
      Get("/tags") ~> as("mary") ~> route("mary") ~> check {
        responseAs[Result[TagList]] match {
          case Success(Some(list), _) => assert(list === DummyStoreActor.tagList)
          case x => sys.error("Invalid response: "+x)
        }
        true
      }
    }
  }
}
