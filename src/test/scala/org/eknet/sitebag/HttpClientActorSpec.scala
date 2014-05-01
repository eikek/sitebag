package org.eknet.sitebag

import akka.testkit.{ImplicitSender, TestKit}
import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import org.scalatest.{WordSpecLike, BeforeAndAfterAll}
import spray.http._
import org.eknet.sitebag.content.ExtractedContent
import commons._

class HttpClientActorSpec extends TestKit(ActorSystem("HttpClientActorSpec", ConfigFactory.load("reference")))
  with WordSpecLike with BeforeAndAfterAll with ImplicitSender {

  val extrRef = system.actorOf(ExtractionActor())
  override def afterAll() = {
    system.shutdown()
  }

  "The fetch-page-actor" must {
    "create a page entry for a web page" in {
      val ref = createHtmlClient(extrRef, "test.html")
      ref ! ExtractRequest("http://dummy")
      expectMsgPF() {
        case Success(Some(ExtractedContent(org, title, text, short, bins)), _) =>
          assert(org.uri === Uri("http://dummy"))
          assert(title === "Robin Hood -- The Outlaw")
          assert(text.nonEmpty)
      }
    }

    "create an error page if page is not found" in {
      val ref = create404Client(extrRef)
      ref ! ExtractRequest("http://dummy")
      expectMsgPF() {
        case Failure(msg, error) =>
          assert(msg.nonEmpty)
      }
    }

    "create an untitled page for weird content" in {
      val ref = createClient(extrRef, HttpResponse(status = StatusCodes.OK, entity = "{ /%&() }"))
      ref ! ExtractRequest("http://dummy")
      expectMsgPF() {
        case Success(Some(ExtractedContent(org, title, text, short, bins)), _) =>
          assert(org.uri === Uri("http://dummy"))
          assert(title === "No title")
          assert(text === "{ /%&() }")
      }
    }
  }
}
