package org.eknet.sitebag.search

import scala.concurrent.duration._
import scala.concurrent.Await
import org.eknet.sitebag._

class SearchActorSpec extends ActorTestBase("SearchActorSpec") with IndexTest with MongoTest {
  import system.dispatcher

  "SearchActor" should {
    "Rebuild indexes" in {
      val ref = system.actorOf(SearchActor(dbname))
      val entry = commons.newEntry
      Await.ready(mongo.addEntry("testuser", entry), 3.seconds)
      ref ! EntrySaved("testuser", entry)
      expectNoMsg()
      ref ! RebuildIndex(None, onlyIfEmpty = false)
      expectMsg(Success("Complete Index Rebuild started"))
      expectMsg(5.seconds, Success("All indexes have been rebuild."))
    }
  }
}
