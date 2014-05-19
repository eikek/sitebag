package org.eknet.sitebag.search

import scala.concurrent.duration._
import scala.concurrent.Await
import org.eknet.sitebag._
import akka.actor.Props

class SearchActorSpec extends ActorTestBase("SearchActorSpec") with MongoTest {
  import system.dispatcher

  val ref = system.actorOf(Props(new SearchActor(mongo) {
    override def preStart() = ()
  }))


  "SearchActor" should {
    "Rebuild indexes" in {
      val entry = commons.newEntry
      Await.ready(mongo.addEntry("testuser", entry), 3.seconds)

      ref ! RebuildIndex(None, onlyIfEmpty = false)
      expectMsgAllOf(
        Success("Complete Index Rebuild started"),
        Success("All indexes have been rebuild.")
      )
    }
  }
}
