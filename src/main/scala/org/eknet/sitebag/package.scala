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
  case class ListEntries(account: Ident, tag: Set[Tag], archived: Option[Boolean], page: Page = Page(1, None), complete: Boolean = true) extends StoreMessage
  case class ListTags(account: Ident, regex: String = ".*") extends StoreMessage
  case class TagList(tags: List[Tag], cloud: Map[Tag, Int])
  case class ReExtractContent(account: Ident, entryId: Option[String]) extends StoreMessage

  //http client actor messages
  case class ExtractRequest(url: Uri) extends Serializable
  case class FetchContent(url: Uri) extends Serializable

  /**
   * Messages received from clients.
   */
  trait SitebagMessage
  case class CreateUser(newaccount: Ident, newpassword: String) extends SitebagMessage
  case class GenerateToken(account: Ident) extends SitebagMessage
  case class ChangePassword(account: Ident, newpassword: String) extends SitebagMessage
  case class Add(account: Ident, page: ExtractRequest, title: Option[String] = None, tags: Set[Tag] = Set.empty) extends SitebagMessage

  //like a `Try` with an additional message
  sealed trait Result[+T] {
    def isSuccess: Boolean
    def isFailure: Boolean
    def message: String
    def map[B](f: Option[T] => Option[B]): Result[B]
    def mapmap[B](f: T => B): Result[B]
    def flatMap[B](f: Option[T] => Result[B]): Result[B]
  }
  type StringResult = Result[String]
  type Ack = Result[Null]
  final case class Failure(customMessage: String, error: Option[Throwable] = None) extends Result[Nothing] {
    require(customMessage.nonEmpty || error.isDefined, "Either an error message or an exception must be supplied")
    val isSuccess = false
    val isFailure = true

    def message = if (customMessage.nonEmpty) customMessage else error.map(_.getMessage).getOrElse("An error occured.")
    def map[B](f: (Option[Nothing]) => Option[B]) = this
    def mapmap[B](f: (Nothing) => B) = this

    def flatMap[B](f: (Option[Nothing]) => Result[B]) = this
  }
  object Failure {
    def apply(error: Throwable): Failure = Failure("", Some(error))
    def apply(message: String): Failure = {
      require(message.nonEmpty, "The error message must not be empty")
      Failure(message, None)
    }
  }
  final case class Success[T](value: Option[T], message: String) extends Result[T] {
    val isSuccess = true
    val isFailure = false

    def map[B](f: (Option[T]) => Option[B]) = Try(f(value)) match {
      case scala.util.Success(b) => Success(b, message)
      case scala.util.Failure(ex) => Failure(ex.getMessage, Some(ex))
    }
    def flatMap[B](f: (Option[T]) => Result[B]) = Try(f(value)) match {
      case scala.util.Success(b) => b
      case scala.util.Failure(ex) => Failure(ex)
    }

    def mapmap[B](f: (T) => B) = value match {
      case Some(v) => Try(f(v)) match {
        case scala.util.Success(b) => Success(Some(b), message)
        case scala.util.Failure(ex) => Failure(ex.getMessage, Some(ex))
      }
      case None => Success[B](None, message)
    }
  }
  object Success {
    def apply[T](value: Option[T]): Success[T] = Success(value, "Operation successful.")
    def apply[T](value: T): Success[T] = Success(Some(value), "Operation successful.")
    def apply(msg: String): Ack = Success(None, msg)
    def apply[T](value: T, msg: String): Success[T] = Success(Some(value), msg)
  }

  //global events
  trait SitebagEvent extends Serializable
  case class EntrySaved(account: Ident, entry: FullPageEntry) extends SitebagEvent
  case class EntryDropped(account: Ident, entryId: String) extends SitebagEvent
  case class EntryTagged(account: Ident, entryId: String, tags: Set[Tag], added: Boolean) extends SitebagEvent
  case class EntryUntagged(account: Ident, entryId: String, tags: Set[Tag]) extends SitebagEvent
  case class EntryArchivedChange(account: Ident, entryId: String, archived: Boolean) extends SitebagEvent
}
