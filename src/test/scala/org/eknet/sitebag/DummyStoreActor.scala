package org.eknet.sitebag

import akka.actor.{Props, Actor}
import org.eknet.sitebag.model.{FullPageEntry, Tag, PageEntry}
import org.eknet.sitebag.content.Content
import akka.util.ByteString

class DummyStoreActor extends Actor {

  def receive: Receive = {
    case r: AddBinary =>
      sender ! Success("Binary saved.")

    case r: AddEntry =>
      sender ! Success("Page added.")

    case r: DropEntry =>
      sender ! Success("Page removed.")

    case r: TagEntry =>
      sender ! Success("Page tagged.")

    case r: UntagEntry =>
      sender ! Success("Page untagged.")

    case ToggleArchived(account, entryId, _) =>
      sender ! Success("Archived status changed.")

    case SetArchived(account, entryId, status, _) =>
      sender ! Success("Archived status changed.")

    case GetEntry(account, entryId) =>
      val obj = if (entryId != DummyStoreActor.existingEntry.id) Success(None)
                else Success(DummyStoreActor.existingEntry)
      sender ! obj
    case GetEntryMeta(account, entryId) =>
      val obj = if (entryId != DummyStoreActor.existingEntry.id) Success(None)
      else Success(DummyStoreActor.existingEntry)
      sender ! obj

    case r: GetEntryContent =>
      val obj = if (r.entryId != DummyStoreActor.existingEntry.id) Success(None)
                else Success(DummyStoreActor.existingContent)
      sender ! obj

    case l: ListEntries =>
      sender ! Success(List(DummyStoreActor.existingEntry))

    case l: ListTags =>
      sender ! Success(DummyStoreActor.tagList)
  }
}

object DummyStoreActor {
  def apply() = Props(classOf[DummyStoreActor])
  val FullPageEntry(existingEntry, existingContent) = commons.newEntry
  val tagList = TagList(List(Tag.favourite), Map(Tag.favourite -> 2))
}