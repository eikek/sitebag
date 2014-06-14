package org.eknet.sitebag

import org.apache.lucene.document.Document
import org.eknet.sitebag.model.{Tag, PageEntry}
import org.eknet.sitebag.lucene._
import spray.http.{DateTime, Uri}
import org.apache.lucene.index.Term
import porter.model.Ident

package object search {

  case class RebuildIndex(account: Option[Ident], onlyIfEmpty: Boolean = false)
  case class CheckRebuildStatus(account: Ident)
  sealed trait RebuildStatus extends Serializable
  case object RebuildIdle extends RebuildStatus
  case class RebuildRunning(added: Int, startedMillis: Long)

  implicit val pageEntryDocumentHandler = new DocumentConverter[PageEntry] {
    def read(doc: Document) = {
      val url = doc("url").map(Uri.apply)
      val title = doc("title").getOrElse("no title")
      val archived = doc("archived").map(_.toBoolean).getOrElse(false)
      val tags = doc.values("tag").map(Tag.apply).toSet
      val short = doc("shortText").getOrElse("...")
      val created = doc.as[Long]("created").map(DateTime.apply).getOrElse(DateTime.now)
      url.map(u => PageEntry(
        title = title,
        url = u,
        content = "",
        shortText = short,
        archived = archived,
        created = created,
        tags = tags
      ))
    }

    def write(value: PageEntry) = {
      val doc = new Document()
      doc += value.id.asField("_id").indexed.stored.notTokenized
      doc += value.title.asField("title").indexed.stored.tokenized
      doc += (value.title +" "+ value.content).asField("content").indexed.notStored.tokenized
      doc += value.archived.asField("archived").indexed.stored.notTokenized
      value.tags.foreach { tag =>
        doc += tag.name.asField("tag").indexed.stored.notTokenized
      }
      doc += value.created.clicks.asField("created").indexed.stored.notTokenized
      val date = value.created.year + value.created.month + value.created.day
      doc += date.asField("date").indexed.notStored.notTokenized
      doc += value.url.toString().asField("url").indexed.stored.notTokenized
      doc += value.url.authority.host.address.asField("host").indexed.notStored.notTokenized
      doc += value.shortText.asField("shortText").stored.notIndexed.tokenized
      doc
    }
  }

  implicit val pageEntryId = new TermCreator[PageEntry] {
    def create(value: PageEntry) = new Term("_id", value.id)
  }

  implicit class PathOps(path: java.nio.file.Path) {
    import java.nio.file.Files
    def notExistsOrEmpty: Boolean =
      !Files.exists(path) || !Files.newDirectoryStream(path).iterator().hasNext
  }
}
