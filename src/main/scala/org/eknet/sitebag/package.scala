package org.eknet

import porter.model.{PasswordCredentials, Ident}
import org.eknet.sitebag.model.{Tag, PageEntry}

package object sitebag {

  /**
   * A marker trait for messages that an actor must understand
   * to implement a storage.
   */
  sealed trait StoreMessage extends Serializable

  case class AddEntry(account: Ident, entry: PageEntry) extends StoreMessage
  case class DropEntry(account: Ident, entry: PageEntry) extends StoreMessage
  case class TagEntry(account: Ident, entryId: String, tags: Set[Tag]) extends StoreMessage
  case class UntagEntry(account: Ident, entryId: String, tags: Set[Tag]) extends StoreMessage
  case class ToggleRead(account: Ident, entryId: String) extends StoreMessage
  case class SetRead(account: Ident, entryId: String, read: Boolean) extends StoreMessage

  case class GetEntry(account: Ident, entryId: String) extends StoreMessage
  case class ListEntries(account: Ident, tag: Option[Tag], read: Boolean)
  case class ListTags(account: Ident) extends StoreMessage


}
