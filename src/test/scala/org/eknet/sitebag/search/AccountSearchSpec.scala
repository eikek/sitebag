package org.eknet.sitebag.search

import scala.concurrent.Await
import scala.concurrent.duration._
import org.eknet.sitebag._
import akka.actor.Props
import porter.model.Ident
import org.eknet.sitebag.lucene.QueryMaker
import org.eknet.sitebag.model.{Tag, PageEntry}
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.store.FSDirectory
import scala.util.Try

class AccountSearchSpec extends ActorTestBase("AccountSearchSpec") with IndexTest with MongoTest {
  import system.dispatcher

  private def createActor(username: Ident) = {
    val indexdir = newIndexDir.toPath
    val ref = system.actorOf(Props(new AccountSearchActor(username, mongo) {
      override protected def makeIndexDir(account: Ident) = indexdir
    }))
    (indexdir, ref)
  }

  "AccountSearchActor" should {
    "add new pages to the index" in {
      val (indexdir, ref) = createActor("testuser")
      val entry = commons.newEntry
      ref ! EntrySaved("testuser", entry)
      expectNoMsg()

      val result = commons.search[PageEntry](indexdir.toFile, QueryMaker.fromString("*", "content"))
      assert(result.toList === List(entry.entry.copy(content = "")))
    }

    "delete entries from index" in {
      val (indexdir, ref) = createActor("testuser")
      val entry = commons.newEntry
      ref ! EntrySaved("testuser", entry)
      expectNoMsg()

      ref ! EntryDropped("testuser", entry.entry.id)
      expectNoMsg()
      val result = commons.search(indexdir.toFile, QueryMaker.fromString("*", "content"))
      assert(result.toList === Nil)
    }

    "update index when entries change" in {
      val (indexdir, ref) = createActor("testuser")
      val entry = commons.newEntry
      Await.ready(mongo.addEntry("testuser", entry), 3.seconds)
      ref ! EntrySaved("testuser", entry)
      expectNoMsg()

      val empty = commons.search(indexdir.toFile, QueryMaker.fromString("tag:"+ Tag.favourite.name, "tag"))
      assert(empty.toList === Nil)

      Await.ready(mongo.tagEntries("testuser", entry.entry.id, Set(Tag.favourite)), 3.seconds)
      ref ! EntryTagged("testuser", entry.entry.id, Set(Tag.favourite), added = true)
      expectNoMsg()

      val result = commons.search[PageEntry](indexdir.toFile, QueryMaker.fromString("tag:"+ Tag.favourite.name, "content"))
      assert(result.toList === List(entry.entry.copy(content = "", tags = Set(Tag.favourite))))
    }

    "not rebuild existing index" in {
      val (indexdir, ref) = createActor("testuser")
      val entry = commons.newEntry
      ref ! EntrySaved("testuser", entry)
      expectNoMsg()

      ref ! RebuildIndex(Some("testuser"), onlyIfEmpty = true)
      expectMsg(Success("Don't rebuild index, since it already exists."))
    }

    "rebuild index" in {
      val (indexdir, ref) = createActor("testuser")
      val entry = commons.newEntry
      Await.ready(mongo.addEntry("testuser", entry), 3.seconds)

      ref ! RebuildIndex(Some("testuser"), onlyIfEmpty = false)
      expectMsgPF(hint = "Success(None, ...)") {
        case Success(_, msg) if msg startsWith "Index rebuild done for" =>
      }
      awaitCond(Try(DirectoryReader.open(FSDirectory.open(indexdir.toFile))).isSuccess, 1.seconds)

      val result = commons.search[PageEntry](indexdir.toFile, QueryMaker.fromString("*", "content"))
      assert(result.toList === List(entry.entry.copy(content = "")))
    }

    "search non existing index dir" in {
      val (indexdir, ref) = createActor("testuser")
      ref ! ListEntries("testuser", Set.empty, None, "*")
      expectMsg(Success(Nil))
    }

    "respond with failure for empty query" in {
      val (indexdir, ref) = createActor("testuser")
      val entry = commons.newEntry
      ref ! EntrySaved("testuser", entry)
      expectNoMsg()

      ref ! ListEntries("testuser", Set.empty, None)
      expectMsgPF(hint = "Failure(...)") {
        case Failure(m, error) =>
      }
    }
  }
}
