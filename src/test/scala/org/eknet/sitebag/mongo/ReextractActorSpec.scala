package org.eknet.sitebag.mongo

import scala.concurrent.duration._
import akka.testkit.{ImplicitSender, TestKit}
import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll, WordSpecLike}
import scala.concurrent.Await
import org.eknet.sitebag._
import org.eknet.sitebag.model.PageEntry
import org.eknet.sitebag.content.Content
import akka.util.ByteString
import org.eknet.sitebag.model.FullPageEntry
import scala.Some

class ReextractActorSpec extends TestKit(ActorSystem("ReextractActorSpec", ConfigFactory.load("reference")))
  with WordSpecLike with BeforeAndAfterAll with BeforeAndAfter with ImplicitSender {

  import system.dispatcher
  val settings = SitebagSettings(system)

  val extrRef = system.actorOf(ExtractionActor())

  before {
    Await.ready(settings.mongoClient.db.drop(), 10.seconds)
  }

  override def afterAll() {
    system.shutdown()
  }

  "The re-extract actor" should {
    "respond to re-extraction requests" in {
      val fentry@FullPageEntry(entry, content) = commons.newEntry
      Await.ready(settings.mongoClient.addEntry("testuser" , fentry), 5.seconds)

      val ref = system.actorOf(ReextractActor(extrRef))
      ref ! ReExtractContent("testuser", None)
      expectMsg(5.seconds, Success("Re-extraction for 'testuser' done."))
    }

    "respond with failure when job is running" in {
      val fentry@FullPageEntry(entry, content) = commons.newEntry
      Await.ready(settings.mongoClient.addEntry("testuser" , fentry), 5.seconds)

      val ref = system.actorOf(ReextractActor(extrRef))
      ref ! ReExtractContent("testuser", None)
      ref ! ReExtractContent("testuser", None)
      expectMsg(5.seconds, Failure("A re-extraction job is already running for you."))
    }

    "restart job after previous is complete" in {
      val fentry@FullPageEntry(entry, content) = commons.newEntry
      Await.ready(settings.mongoClient.addEntry("testuser" , fentry), 5.seconds)

      val ref = system.actorOf(ReextractActor(extrRef))
      ref ! ReExtractContent("testuser", None)
      expectMsg(5.seconds, Success("Re-extraction for 'testuser' done."))
      //the response is sent right before the actorref is removed from the map
      //so just wait a few millis or refactor it
      Thread.sleep(500)
      ref ! ReExtractContent("testuser", None)
      expectMsg(5.seconds, Success("Re-extraction for 'testuser' done."))
    }
  }
}
