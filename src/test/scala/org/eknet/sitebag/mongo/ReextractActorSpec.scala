package org.eknet.sitebag.mongo

import porter.model.Ident
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

      val ref = system.actorOf(ReextractActor(extrRef, mongo))
      ref ! ReExtractContent("testuser", None)
      expectMsg(Success("Re-extraction started for 'testuser'."))
      expectRunningState()
    }

    "respond with failure when job is running" in {
      val fentry@FullPageEntry(entry, content) = commons.newEntry
      Await.ready(mongo.addEntry("testuser" , fentry), 5.seconds)

      val ref = system.actorOf(ReextractActor(extrRef, mongo))
      ref ! ReExtractContent("testuser", None)
      expectMsg(Success("Re-extraction started for 'testuser'."))
      ref ! ReExtractContent("testuser", None)
      expectMsg(5.seconds, Failure("A re-extraction job is already running for you."))
      expectRunningState()
    }

    "restart job after previous is complete" in {
      val fentry@FullPageEntry(entry, content) = commons.newEntry
      Await.ready(mongo.addEntry("testuser" , fentry), 5.seconds)

      val ref = system.actorOf(ReextractActor(extrRef, mongo))
      ref ! ReExtractContent("testuser", None)
      expectMsg(Success("Re-extraction started for 'testuser'."))
      expectRunningState()
      def f =(ref ? ReExtractContent("testuser", None)).mapTo[Ack]
      awaitCond(Await.result(f.map(r => r.isSuccess && r.message == "Re-extraction started for 'testuser'."), 4.seconds), 12.seconds)
    }

    "return running state" in {
      val fentry@FullPageEntry(entry, content) = commons.newEntry
      Await.ready(mongo.addEntry("testuser" , fentry), 5.seconds)

      val ref = system.actorOf(ReextractActor(extrRef, mongo))
      ref ! ReExtractContent("testuser", None)
      expectMsg(Success("Re-extraction started for 'testuser'."))

      ref ! ReExtractStatusRequest("testuser")
      expectRunningState("Operation successful.")
      expectRunningState()
    }
  }

  def expectRunningState(text: String = "Re-extraction for 'testuser' completed.") {
    expectMsgPF(hint = s"Success(Running(), $text)") {
      case Success(Some(ReExtractStatus.Running(Ident("testuser"), _, _, _)), msg) ⇒
        assert(msg === text)
        
    }
  }
}
