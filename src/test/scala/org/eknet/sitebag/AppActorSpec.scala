package org.eknet.sitebag

import org.jsoup.Jsoup
import scala.concurrent.Await
import scala.concurrent.duration._
import akka.testkit.{ ImplicitSender, TestKit }
import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import org.scalatest.{ BeforeAndAfter, BeforeAndAfterAll, WordSpecLike }
import org.eknet.sitebag.search.SearchActor

class AppActorSpec extends ActorTestBase("AppActorSpec") with MongoTest {

  import commons._
  import system.dispatcher
  val extrRef = system.actorOf(ExtractionActor())

  override val storeRef = system.actorOf(DummyStoreActor())
  val search = system.actorOf(SearchActor(mongo))

  "The app actor" should {
    "fetch and store pages" in {
      val clientRef = createHtmlClient(extrRef, "test.html")
      val appRef = system.actorOf(AppActor(clientRef, storeRef, search, settings))
      val entry = commons.newEntry.entry
      appRef ! Add("testaccount", ExtractRequest(entry.url))
      expectMsg(Success(entry.id, "Page added."))
    }

    "tell if the page already exists" in {
      val clientRef = createHtmlClient(extrRef, "test.html")
      val appRef = system.actorOf(AppActor(clientRef, storeRef, search, settings))
      appRef ! Add("testaccount", ExtractRequest(DummyStoreActor.existingEntry.url))
      expectMsg(Success(DummyStoreActor.existingEntry.id, "Document does already exist."))
    }

    "return an error for 404 response" in {
      val clientRef = create404Client(extrRef)
      val appRef = system.actorOf(AppActor(clientRef, storeRef, search, settings))
      appRef ! Add("testaccount", ExtractRequest("http://dummy"))
      expectMsgPF(5.seconds) {
        case Failure(msg, _) if msg contains "404" => true
      }
    }

    "rewrite known external urls into internal ones in page contents" in {
      val url = DummyStoreActor.existingEntry.url
      val testcontent = s"""bla bla bla <a href="$url">a link</a> blup blup blup"""
      val entry = commons.newEntry.entry.copy(content = testcontent)

      val Success(Some(result), _) = Await.result(AppActor.rewriteLinks(settings, storeRef, "testuser")(entry), 5.seconds)
      //jsoup puts a newline before the a-element...
      val expected = s"""bla bla bla \n<a href="http://0.0.0.0:9995/ui/entry/${DummyStoreActor.existingEntry.id}" title="$url">a link</a> blup blup blup"""
      assert(result === entry.copy(content = expected))
    }

    "add a link after each unknown external link in page contents" in {
      val testcontent = s"""bla bla bla <a href="http://check.me/d.html">a link</a> blup blup blup"""
      val entry = commons.newEntry.entry.copy(content = testcontent)

      val Success(Some(result), _) = Await.result(AppActor.rewriteLinks(settings, storeRef, "testuser")(entry), 5.seconds)
      val expected = "bla bla bla \n<a href=\"http://check.me/d.html\">a link</a>\n"+
                     "<a href=\"#\" class=\"sb-add-entry-link\" data-id=\"http://check.me/d.html\" "+
                     "title=\"Add to SiteBag\">&nbsp;<span class=\"glyphicon glyphicon-download-alt small\"></span></a> blup blup blup"
      assert(result === entry.copy(content = expected))
    }
  }
}
