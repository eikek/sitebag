package org.eknet.sitebag.model

import spray.http.{DateTime, Uri}
import porter.util.Hash
import org.eknet.sitebag.content.Content

final case class PageEntry(title: String,
                     url: Uri,
                     content: String,
                     shortText: String,
                     archived: Boolean = false,
                     created: DateTime = DateTime.now,
                     tags: Set[Tag] = Set.empty) {

  require(title.nonEmpty, "title is required")

  val id = PageEntry.makeId(url)

  val tagsSorted = tags.toList.sortBy(_.name)

  def withTags(t1: Tag, ts: Tag*) = copy(tags = tags ++ (t1 +: ts))

  def toFullEntry(page: Content) = FullPageEntry(this, page)
}
object PageEntry {
  def makeId(url: String): String = Hash.md5String(url)
  def makeId(url: Uri): String = makeId(url.toString())
}
case class FullPageEntry(entry: PageEntry, page: Content)
