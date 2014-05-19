package org.eknet.sitebag

import spray.http.Uri
import org.eknet.sitebag.model.{Page, Token, Tag}
import porter.model.{PasswordCredentials, Ident}
import scala.util.Try

package object rest {

  case class RestContext(authId: Ident, subject: Ident, token: Option[Token])

  final case class TokenCredentials(username: String, token: String) extends PasswordCredentials {
    def password = token
    def accountName = username
  }
  final case class UserPassCredentials(username: String, password: String) extends PasswordCredentials {
    def accountName = username
  }

  // ~~~~~~~ requests
  type Fields = Seq[(String, String)]
  trait FieldsDeserialize[A] { def fromFields(fields: Fields): A }

  case class NewPassword(password: String)
  case class RAdd(url: Uri, title: Option[String], tags: Set[Tag])
  case class Flag(flag: Boolean)
  case class TagInput(tags: Set[Tag]){
    def ++ (other: Set[Tag]): TagInput = TagInput(tags ++ other)
    def ++ (other: TagInput): TagInput = this ++ other.tags
  }
  object TagInput extends FieldsDeserialize[TagInput] {
    val empty = apply(Set.empty)
    def fromFields(fields: Fields): TagInput = {
      val tags = fields.collect { case (k, v) if k == "tag" => Tag(v) }
      val data = fields.toMap
      val other = data.get("tags").map(_.split(',').map(s => Tag(s.trim)))
      TagInput(tags.toSet ++ other.getOrElse(Array.empty).toSet)
    }
  }
  case class TagFilter(filter: String)
  object TagFilter extends FieldsDeserialize[TagFilter]  {
    val all = TagFilter(".*")
    def fromFields(fields: Fields) = fields.toMap.get("filter").map(TagFilter.apply).getOrElse(all)
  }
  case class EntrySearch(tag: TagInput, archived: Option[Boolean], query: String, page: Option[Page], complete: Boolean) {
    def toListEntries(subject: Ident): ListEntries =
      ListEntries(subject, tag.tags, archived, query, page.getOrElse(Page(1, None)), complete)
    def withTags(tag: Tag, tags: Tag*) = copy(tag = TagInput((tag +: tags).toSet))
  }
  object EntrySearch extends FieldsDeserialize[EntrySearch]  {
    val allNew = EntrySearch(TagInput.empty, Some(false), "", None, true)
    val empty = EntrySearch(TagInput(Set.empty), None, "", None, false)
    def fromFields(fields: Fields): EntrySearch = {
      val tagin = TagInput.fromFields(fields)
      val fmap = fields.toMap
      val archived = fmap.get("archived").flatMap {
        case "true" => Some(true)
        case "false" => Some(false)
        case _ => None
      }
      val pnum = Try(fmap("num").toInt).toOption
      val psize = Try(fmap("size").toInt).toOption
      val page = pnum.map(Page(_, psize))
      val query = fmap.get("q").getOrElse("")
      val complete = fmap.get("complete").map(_.toBoolean).getOrElse(false)
      EntrySearch(tagin, archived, query, page, complete)
    }
  }

  case class DeleteAction(delete: Boolean)
  case class ReextractAction(entryId: Option[String])
}
