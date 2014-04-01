package org.eknet.sitebag.model

import spray.http.{DateTime, Uri}
import java.io.{StringWriter, PrintWriter}
import porter.util.Hash

case class PageEntry(id: String,
                     title: String,
                     url: Uri,
                     content: String,
                     read: Boolean = false,
                     created: DateTime = DateTime.now,
                     tags: Set[Tag] = Set.empty) {

  def withTags(t1: Tag, ts: Tag*) = copy(tags = tags ++ (t1 +: ts))
  def unread = if (read) copy(read = false) else this

}

object PageEntry {

  def failure(url: Uri, error: Option[Throwable] = None): PageEntry = {
    val title = s"Fetching '$url' failed"
    val now = DateTime.now
    val content = s"""<h1>$title <small>$now</small></h1><p>Sorry, the page at <a href="$url">$url</a> could not be read.</p>""" + (error match {
      case Some(ex) =>
        s"<p>Error: <pre>${stacktrace(ex)}</pre></p>"
      case _ => "<p>Unknown error.</p>"
    })
    val hash = Hash.md5String(title + content)
    PageEntry(hash, title, url, content, read = false, created = now)
  }

  private def stacktrace(ex: Throwable) = {
    val sw = new StringWriter()
    val pw = new PrintWriter(sw)
    ex.printStackTrace(pw)
    sw.toString
  }
}
