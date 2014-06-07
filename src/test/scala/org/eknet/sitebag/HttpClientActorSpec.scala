package org.eknet.sitebag

import akka.actor.ActorSystem
import akka.testkit.{ ImplicitSender, TestKit }
import com.typesafe.config.ConfigFactory
import commons._
import org.eknet.sitebag.content.ExtractedContent
import org.scalatest.{ BeforeAndAfterAll, WordSpecLike }
import spray.http._

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
        case Success(Some(ExtractedContent(org, title, text, short, _, bins)), _) =>
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
      println(HttpResponse(status = StatusCodes.OK, entity = "{ /%&() }"))
      ref ! ExtractRequest("http://dummy")
      expectMsgPF() {
        case Success(Some(ExtractedContent(org, title, text, short, _, bins)), _) =>
          assert(org.uri === Uri("http://dummy"))
          assert(title === "http://dummy")
          assert(text === "<p>{ /%&() }</p>")
        case Failure(_, Some(ex)) => ex.printStackTrace()
        case other => sys.error("Problem: " + other)
      }
    }
  }
}
