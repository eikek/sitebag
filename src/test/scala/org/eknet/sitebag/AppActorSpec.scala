package org.eknet.sitebag

import scala.concurrent.duration._
import akka.testkit.{ImplicitSender, TestKit}
import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll, WordSpecLike}
import org.eknet.sitebag.search.SearchActor

class AppActorSpec extends ActorTestBase("AppActorSpec") with MongoTest {

  import commons._
  val extrRef = system.actorOf(ExtractionActor())

  override val storeRef = system.actorOf(DummyStoreActor())
  val search = system.actorOf(SearchActor(mongo))

  "The app actor" should {
    "fetch and store pages" in {
      val clientRef = createHtmlClient(extrRef, "test.html")
      val appRef = system.actorOf(AppActor(clientRef, storeRef, search))
      val entry = commons.newEntry.entry
      appRef ! Add("testaccount", ExtractRequest(entry.url))
      expectMsg(Success(entry.id, "Page added."))
    }

    "tell if the page already exists" in {
      val clientRef = createHtmlClient(extrRef, "test.html")
      val appRef = system.actorOf(AppActor(clientRef, storeRef, search))
      appRef ! Add("testaccount", ExtractRequest(DummyStoreActor.existingEntry.url))
      expectMsg(Success(DummyStoreActor.existingEntry.id, "Document does already exist."))
    }

    "return an error for 404 response" in {
      val clientRef = create404Client(extrRef)
      val appRef = system.actorOf(AppActor(clientRef, storeRef, search))
      appRef ! Add("testaccount", ExtractRequest("http://dummy"))
      expectMsgPF(5.seconds) {
        case Failure(msg, _) if msg contains "404" => true
      }
    }
  }
}
