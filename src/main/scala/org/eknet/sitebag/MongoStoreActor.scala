package org.eknet.sitebag

import akka.actor.{Props, ActorLogging, Actor}
import org.eknet.sitebag.model.{Tag, PageEntry, Result}
import com.mongodb.casbah.MongoClient
import spray.http.{DateTime, Uri}

object MongoStoreActor {
  def apply(mongo: MongoClient, dbname: String) =
    Props(classOf[MongoStoreActor], mongo, dbname)
}
class MongoStoreActor(mongo: MongoClient, dbname: String) extends Actor with ActorLogging {

  def receive = {
    case AddEntry(account, entry) =>
      sender ! Result.success("Page added")

    case DropEntry(account, entry) =>
      sender ! Result.success("Page removed")

    case TagEntry(account, entryId, tags) =>
      sender ! Result.success("Page tagged")

    case UntagEntry(account, entryId, tags) =>
      sender ! Result.success("Page untagged")

    case ToggleRead(account, entryId) =>
      sender ! Result.success("Read status changed")

    case SetRead(account, entryId, status) =>
      sender ! Result.success("Read status changed")

    case GetEntry(account, entryId) =>
      sender ! PageEntry("1", "title", Uri("http://none"), "", false, DateTime.now, Set(Tag.favourite))

    case ListEntries(account, tag, read) =>
      sender ! List(PageEntry("1", "title", Uri("http://none"), "", read, DateTime.now, Set(Tag.favourite)))

    case ListTags(account) =>
      sender ! List(Tag.favourite)
  }
}
