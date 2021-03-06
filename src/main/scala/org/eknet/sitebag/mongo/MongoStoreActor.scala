package org.eknet.sitebag.mongo

import scala.concurrent.duration._
import akka.actor._
import akka.util.Timeout
import akka.pattern.pipe
import org.eknet.sitebag._

object MongoStoreActor {
  def apply(mongo: SitebagMongo): Props = Props(classOf[MongoStoreActor], mongo)
}
class MongoStoreActor(mongo: SitebagMongo) extends Actor with ActorLogging {
  import context.dispatcher

  private implicit val timeout: Timeout = 5.seconds

  def receive = {
    case AddBinary(account, entryId, bin) =>
      mongo.addEntryBinary(account, entryId, bin) pipeTo sender

    case AddEntry(account, entry) =>
      mongo.addEntry(account, entry) pipeTo sender

    case DropEntry(account, entryId) =>
      mongo.deleteEntry(account, entryId) pipeTo sender

    case TagEntry(account, entryId, tagnames) =>
      mongo.tagEntries(account, entryId, tagnames) pipeTo sender

    case UntagEntry(account, entryId, tagnames) =>
      mongo.untagEntries(account, entryId, tagnames) pipeTo sender

    case ToggleArchived(account, entryId, ts) =>
      mongo.toggleArchivedFlag(account, entryId, ts) pipeTo sender

    case SetArchived(account, entryId, status, ts) =>
      mongo.setArchivedFlag(account, entryId, ts, status) pipeTo sender

    case GetEntry(account, entryId) =>
      mongo.getEntry(account, entryId) pipeTo sender

    case GetEntryMeta(account, entryId) =>
      mongo.getEntryMeta(account, entryId) pipeTo sender

    case GetEntryContent(account, entryId) =>
      mongo.getEntryContent(account, entryId) pipeTo sender

    case GetBinaryById(id) =>
      mongo.findBinaryById(id) pipeTo sender

    case GetBinaryByUrl(url) =>
      mongo.findBinaryByUrl(url) pipeTo sender

    case ListEntries(account, tagnames, read, query, page, complete) =>
      mongo.listEntries(account, tagnames, read, page, complete) pipeTo sender

    case GetTags(account, entryId) =>
      mongo.getEntryTags(account, entryId) pipeTo sender

    case SetTags(account, entryId, tagnames) =>
      mongo.setTags(account, entryId, tagnames) pipeTo sender

    case ListTags(account, regex) =>
      mongo.listTags(account, regex) pipeTo sender

  }
}
