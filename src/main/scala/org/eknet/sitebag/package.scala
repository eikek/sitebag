package org.eknet

import porter.model.Ident
import org.eknet.sitebag.model._
import spray.http._
import spray.http.Uri.Path
import porter.util.Hash
import scala.util.Try
import org.eknet.sitebag.content.Extractor
import scala.Some
import org.eknet.sitebag.model.FullPageEntry

package object sitebag {

  /**
   * A marker trait for messages that an actor must understand
   * to implement a storage.
   */
  sealed trait StoreMessage extends Serializable
  case class AddEntry(account: Ident, entry: FullPageEntry) extends StoreMessage
  case class DropEntry(account: Ident, entryId: String) extends StoreMessage
  case class TagEntry(account: Ident, entryId: String, tags: Set[Tag]) extends StoreMessage
  case class UntagEntry(account: Ident, entryId: String, tags: Set[Tag]) extends StoreMessage
  case class ToggleArchived(account: Ident, entryId: String, timestamp: DateTime = DateTime.now) extends StoreMessage
  case class SetArchived(account: Ident, entryId: String, archived: Boolean, timestamp: DateTime = DateTime.now) extends StoreMessage
  case class GetEntry(account: Ident, entryId: String) extends StoreMessage
  case class GetEntryMeta(account: Ident, entryId: String) extends StoreMessage
  case class GetTags(account: Ident, entryId: String) extends StoreMessage
  case class SetTags(account: Ident, entryId: String, tags: Set[Tag]) extends StoreMessage
  case class GetEntryContent(account: Ident, entryId: String) extends StoreMessage
  case class AddBinary(account: Ident, entryId: String, bin: Binary) extends StoreMessage
  case class GetBinaryById(id: String) extends StoreMessage
  case class GetBinaryByUrl(url: String) extends StoreMessage
  case class ListEntries(account: Ident, tag: Set[Tag], archived: Option[Boolean], query: String = "", page: Page = Page(1, None), complete: Boolean = true) extends StoreMessage
  case class ListTags(account: Ident, regex: String = ".*") extends StoreMessage
  case class TagList(tags: List[Tag], cloud: Map[Tag, Int])
  case class ReExtractContent(account: Ident, entryId: Option[String]) extends StoreMessage

  case class AccountList(names: List[Ident])

  //http client actor messages
  case class ExtractRequest(url: Uri) extends Serializable
  case class FetchContent(url: Uri) extends Serializable

  type StringResult = Result[String]
  type Ack = Result[Null]

  /**
   * Messages received from clients.
   */
  trait SitebagMessage
  case class CreateUser(newaccount: Ident, newpassword: String) extends SitebagMessage
  case class DeleteUser(account: Ident) extends SitebagMessage
  case class GenerateToken(account: Ident) extends SitebagMessage
  case class ChangePassword(account: Ident, newpassword: String) extends SitebagMessage
  case class Add(account: Ident, page: ExtractRequest, title: Option[String] = None, tags: Set[Tag] = Set.empty) extends SitebagMessage

  //global events
  sealed trait SitebagEvent extends Serializable {
    def account: Ident
  }
  sealed trait SitebagEntryEvent extends SitebagEvent {
    def entryId: String
  }
  case class EntrySaved(account: Ident, entry: FullPageEntry) extends SitebagEntryEvent {
    val entryId = entry.entry.id
  }
  case class EntryDropped(account: Ident, entryId: String) extends SitebagEntryEvent
  case class EntryTagged(account: Ident, entryId: String, tags: Set[Tag], added: Boolean) extends SitebagEntryEvent
  case class EntryUntagged(account: Ident, entryId: String, tags: Set[Tag]) extends SitebagEntryEvent
  case class EntryArchivedChange(account: Ident, entryId: String, archived: Boolean) extends SitebagEntryEvent
  case class EntryContentsChange(account: Ident, entryId: String) extends SitebagEntryEvent
  case class ReextractionDone(account: Ident) extends SitebagEvent
}
