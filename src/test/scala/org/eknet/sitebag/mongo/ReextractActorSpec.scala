package org.eknet.sitebag.mongo

import scala.concurrent.duration._
import akka.testkit.{ImplicitSender, TestKit}
import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll, WordSpecLike}
import scala.concurrent.Await
import org.eknet.sitebag._
import akka.util.Timeout
import org.eknet.sitebag.model.FullPageEntry

class ReextractActorSpec extends ActorTestBase("ReExtractActorTest") with MongoTest {

  import system.dispatcher
  import akka.pattern.ask

  val extrRef = system.actorOf(ExtractionActor())

  "The re-extract actor" should {
    "respond to re-extraction requests" in {
      val fentry@FullPageEntry(entry, content) = commons.newEntry
      Await.ready(mongo.addEntry("testuser" , fentry), 5.seconds)

      val ref = system.actorOf(ReextractActor(extrRef, dbname))
      ref ! ReExtractContent("testuser", None)
      expectMsg(5.seconds, Success("Re-extraction for 'testuser' done."))
    }

    "respond with failure when job is running" in {
      val fentry@FullPageEntry(entry, content) = commons.newEntry
      Await.ready(mongo.addEntry("testuser" , fentry), 5.seconds)

      val ref = system.actorOf(ReextractActor(extrRef, dbname))
      ref ! ReExtractContent("testuser", None)
      ref ! ReExtractContent("testuser", None)
      expectMsg(5.seconds, Failure("A re-extraction job is already running for you."))
    }

    "restart job after previous is complete" in {
      val fentry@FullPageEntry(entry, content) = commons.newEntry
      Await.ready(mongo.addEntry("testuser" , fentry), 5.seconds)

      val ref = system.actorOf(ReextractActor(extrRef, dbname))
      ref ! ReExtractContent("testuser", None)
      expectMsg(5.seconds, Success("Re-extraction for 'testuser' done."))
      def f =(ref ? ReExtractContent("testuser", None)).mapTo[Ack]
      awaitCond(Await.result(f.map(r => r.isSuccess && r.message == "Re-extraction for 'testuser' done."), 4.seconds), 12.seconds)
    }
  }
}
