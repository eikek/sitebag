package org.eknet.sitebag.mongo

import scala.concurrent.duration._
import akka.testkit.{ImplicitSender, TestKit}
import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll, WordSpecLike}
import reactivemongo.bson.BSONDocument
import org.eknet.sitebag.model._
import spray.http.{ContentTypes, DateTime, MediaTypes, ContentType}
import akka.util.ByteString
import scala.util.Try
import scala.concurrent.{Future, Await}
import org.eknet.sitebag._
import org.eknet.sitebag.content.Content
import org.eknet.sitebag.model.FullPageEntry

class MongoStoreActorSpec extends TestKit(ActorSystem("MongoStoreActorSpec", ConfigFactory.load("reference")))
  with WordSpecLike with BeforeAndAfterAll with BeforeAndAfter with ImplicitSender {

  import system.dispatcher
  val storeRef = system.actorOf(MongoStoreActor())
  val settings = SitebagSettings(system)

  before {
    Await.ready(settings.mongoClient.db.drop(), 10.seconds)
  }

  override def afterAll() = {
    system.shutdown()
  }

  "A StoreActor" must {
    "add and retrieve entries" in {
      val fentry@FullPageEntry(entry, c) = commons.newEntry
      storeRef ! AddEntry("testuser", fentry)
      expectMsg(5.seconds, Success("Page added."))

      storeRef ! GetEntry("testuser", entry.id)
      expectMsg(5.seconds, Success(Some(entry)))

      storeRef ! GetEntry("testuser15", entry.id)
      expectMsg(5.seconds, Success(None))

      Thread.sleep(1090)
      storeRef ! GetEntryContent("testuser", entry.id)
      expectMsgPF(hint = "Success(Content(..))") {
        case Success(Some(content: Content), _) =>
          assert(content.data === c.data)
      }
    }

    "store multiple same binaries once" in {
      val bin1 = Binary("12345", "http://google.com/image", ByteString("bla"), "12345", ContentTypes.`application/octet-stream`)
      val bin2 = bin1.copy(url = "http://ddg.gg/image")

      Await.ready(settings.mongoClient.addBinary(bin1), 5.seconds)
      Await.ready(settings.mongoClient.addBinary(bin2), 5.seconds)
      val Success(Some(xbin1), _) = Await.result(settings.mongoClient.findBinaryByUrl(bin1.url.toString()), 5.seconds)
      val Success(Some(xbin2), _) = Await.result(settings.mongoClient.findBinaryByUrl(bin2.url.toString()), 5.seconds)
      assert(xbin1.id === xbin2.id)
      assert(xbin1.id === bin2.id)
      val all:Future[List[BSONDocument]] = settings.mongoClient.db("fs.files").find(BSONDocument()).cursor[BSONDocument].collect[List]()
      val list = Await.result(all, 4.seconds)
      assert(list.length === 1)
    }


    "ignore duplicate content" in {
      val fentry@FullPageEntry(entry, content) = commons.newEntry
      storeRef ! AddEntry("testuser", fentry)
      expectMsg(5.seconds, Success("Page added."))
      storeRef ! AddEntry("testuser", FullPageEntry(entry.copy(url = "https://moredummy"), Content("http://dumy2", ByteString("bla bla2"))))
      expectMsg(5.seconds, Success("Page added."))
    }

    "ignore duplicate entries" in {
      val fentry@FullPageEntry(entry, content) = commons.newEntry
      storeRef ! AddEntry("testuser", fentry)
      expectMsg(5.seconds, Success("Page added."))
      storeRef ! AddEntry("testuser", fentry)
      expectMsg(5.seconds, Success("Page already present."))
    }

    "Delete entries" in {
      awaitCond(Await.result(settings.mongoClient.countBinaries().map(_ == 0), 4.seconds), 4.seconds)
      var numsZero = Await.result(settings.mongoClient.countBinaries(), 5.seconds)
      assert(numsZero === 0)
      val fentry@FullPageEntry(entry, content) = commons.newEntry
      storeRef ! AddEntry("testuser", fentry)
      expectMsg(5.seconds, Success("Page added."))
      Thread.sleep(1500) //make sure content-id is set
      numsZero = Await.result(settings.mongoClient.countBinaries(), 5.seconds)
      assert(numsZero === 1)
      storeRef ! AddBinary("testuser", entry.id, Binary(Content("http://anotherfile", ByteString("blup blup"))))
      expectMsg(Success(None, "Binary saved."))
      numsZero = Await.result(settings.mongoClient.countBinaries(), 5.seconds)
      assert(numsZero === 2)
      storeRef ! GetEntryContent("testuser", entry.id)
      expectMsgPF(hint = "Success(Some(Content(...)))") { case Success(Some(c: Content), _) => true }

      storeRef ! DropEntry("testuser", entry.id)
      expectMsg(Success("Page removed."))
      storeRef ! GetEntry("testuser", entry.id)
      expectMsg(Success(None))
      numsZero = Await.result(settings.mongoClient.countBinaries(), 5.seconds)
      assert(numsZero === 0)
      storeRef ! GetBinaryById(Binary(content).id)
      expectMsg(Success(None))
    }

    "tag entries" in {
      val fentry@FullPageEntry(entry, content) = commons.newEntry
      storeRef ! AddEntry("testuser", fentry)
      expectMsg(5.seconds, Success("Page added."))
      val mytags = Set(Tag("programming"), Tag("outdoor"))
      storeRef ! TagEntry("testuser", entry.id, mytags)
      expectMsg(Success("Page tagged."))

      val entryWithTags = entry.copy(tags = mytags)
      storeRef ! ListTags("testuser")
      expectMsg(Success(TagList(mytags.toList.sortBy(_.name), mytags.map(_ -> 1).toMap)))
      storeRef ! ListTags("testuser", "pro.*?ing")
      expectMsg(Success(TagList(List(Tag("programming")), Map(Tag("programming") -> 1))))
      storeRef ! GetEntry("testuser", entry.id)
      expectMsg(5.seconds, Success(entryWithTags))
      storeRef ! ListEntries("testuser", Set(Tag("programming")), Some(false))
      expectMsg(Success(List(entryWithTags)))
      storeRef ! ListEntries("testuser", Set(Tag("outdoor")), Some(false))
      expectMsg(Success(List(entryWithTags)))
      storeRef ! ListEntries("testuser", mytags, Some(false))
      expectMsg(Success(List(entryWithTags)))
    }

    "return empty list for unknown tags" in {
      storeRef ! ListEntries("testuser", Set(Tag("mdnwe234ds")), Some(false))
      expectMsg(Success(Nil))
    }

    "untag entries" in {
      val fentry@FullPageEntry(entry, content) = commons.newEntry
      storeRef ! AddEntry("testuser", fentry)
      expectMsg(5.seconds, Success("Page added."))
      val mytags = Set(Tag("zebra"), Tag("cooking"), Tag("fence"))
      storeRef ! TagEntry("testuser", entry.id, mytags)
      expectMsg(Success("Page tagged."))

      storeRef ! GetEntry("testuser", entry.id)
      expectMsg(5.seconds, Success(entry.copy(tags = mytags)))

      storeRef ! UntagEntry("testuser", entry.id, Set(Tag("zebra"), Tag("fence")))
      expectMsg(Success("Page untagged."))
      storeRef ! ListEntries("testuser", Set(Tag("zebra")), Some(false))
      expectMsg(Success(Nil))
      storeRef ! ListEntries("testuser", Set(Tag("fence")), Some(false))
      expectMsg(Success(Nil))
      storeRef ! GetEntry("testuser", entry.id)
      expectMsg(5.seconds, Success(entry.copy(tags = Set(Tag("cooking")))))

      storeRef ! ListTags("testuser")
      expectMsg(Success(TagList(List(Tag("cooking")), Map(Tag("cooking") -> 1))))

      storeRef ! GetTags("testuser", entry.id)
      expectMsg(Success(List(Tag("cooking"))))
    }

    "list tags for an entry" in {
      val fentry@FullPageEntry(entry, content) = commons.newEntry
      val f = settings.mongoClient.addEntry("testuser", fentry)
      Await.ready(f, 5.seconds)

      Await.ready(settings.mongoClient.tagEntries("testuser", entry.id, Set(Tag("x"), Tag("zz"))), 5.seconds)

      storeRef ! GetTags("testuser", entry.id)
      expectMsg(Success(List(Tag("x"), Tag("zz"))))
    }

    "set read flag" in {
      val fentry@FullPageEntry(entry, content) = commons.newEntry
      storeRef ! AddEntry("testuser", fentry)
      expectMsg(5.seconds, Success("Page added."))

      storeRef ! SetArchived("testuser", entry.id, true)
      expectMsg(Success("Archived status changed."))
      storeRef ! ListEntries("testuser", Set.empty, Some(true))
      expectMsg(Success(List(entry.copy(archived = true))))

      storeRef ! ToggleArchived("testuser", entry.id)
      expectMsg(Success("Archived status changed."))
      storeRef ! ListEntries("testuser", Set.empty, Some(true))
      expectMsg(Success(Nil))
    }

    "retrieve original page of an entry" in {
      val fentry@FullPageEntry(entry, content) = commons.newEntry
      storeRef ! AddEntry("testuser", fentry)
      expectMsg(5.seconds, Success("Page added."))
      Try(awaitCond(false, 1.seconds))
      storeRef ! GetEntryContent("testuser", entry.id)
      expectMsgPF(hint = "Success(Some(Content(...)))") {
        case Success(Some(Content(uri, ct, data)), _) =>
          assert(uri === entry.url)
          assert(ct.get === ContentType(MediaTypes.`text/html`))
          assert(data === content.data)
      }
    }

    "clean tags up properly" in {
      val fentry@FullPageEntry(entry, content) = commons.newEntry
      val mytags = Set(Tag("one"), Tag("two"), Tag("three"))
      storeRef ! AddEntry("testuser", fentry)
      expectMsg(5.seconds, Success("Page added."))
      Try(awaitCond(false, 1.seconds))

      storeRef ! GetEntry("testuser", entry.id)
      expectMsg(5.seconds, Success(entry))

      storeRef ! TagEntry("testuser", entry.id, mytags)
      expectMsg(Success("Page tagged."))

      storeRef ! GetEntry("testuser", entry.id)
      expectMsg(5.seconds, Success(entry.copy(tags = mytags)))

      storeRef ! DropEntry("testuser", entry.id)
      expectMsg(Success("Page removed."))

      storeRef ! ListTags("testuser")
      expectMsg(Success(TagList(Nil, Map.empty), "Operation successful."))
    }

    "dont update archived flag with old update" in {
      val fentry@FullPageEntry(entry, content) = commons.newEntry
      storeRef ! AddEntry("testuser", fentry)
      expectMsg(5.seconds, Success("Page added."))
      Thread.sleep(800)

      storeRef ! SetArchived("testuser", entry.id, true, DateTime.now - (3 * 24 * 60 * 60 * 1000L))
      expectMsg(Success("Archived status unchanged."))
      storeRef ! ListEntries("testuser", Set.empty, Some(true))
      expectMsg(Success(Nil))
      storeRef ! ListEntries("testuser", Set.empty, Some(false))
      expectMsg(Success(entry :: Nil))
    }

    "list with multiple tags" in {
      val fentry1@FullPageEntry(entry1, _) = commons.newEntry
      storeRef ! AddEntry("testuser", fentry1)
      expectMsg(5.seconds, Success("Page added."))

      val fentry2@FullPageEntry(entry2, _) = commons.newEntry
      storeRef ! AddEntry("testuser", fentry2)
      expectMsg(5.seconds, Success("Page added."))

      storeRef ! TagEntry("testuser", entry1.id, Set(Tag("salami")))
      expectMsg(4.seconds, Success("Page tagged."))
      storeRef ! TagEntry("testuser", entry2.id, Set(Tag("apple")))
      expectMsg(4.seconds, Success("Page tagged."))

      storeRef ! ListEntries("testuser", Set(Tag("apple")), None)
      expectMsg(Success(List(entry2.copy(tags = Set(Tag("apple"))))))
      storeRef ! ListEntries("testuser", Set(Tag("salami")), None)
      expectMsg(Success(List(entry1.copy(tags = Set(Tag("salami"))))))

      storeRef ! ListEntries("testuser", Set(Tag("apple"), Tag("salami")), None)
      expectMsg(Success(Nil))

      storeRef ! ListEntries("testuser", Set(Tag("cheese")), None)
      expectMsg(Success(Nil))
    }
  }
}
