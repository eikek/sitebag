package org.eknet.sitebag.content

import org.jsoup.nodes.Document
import spray.http.Uri
import org.jsoup.select.Elements
import scala.util.control.NonFatal
import org.eknet.sitebag.utils._

object Html {

  def findBinaries(base: Uri, el: Elements): Set[Uri] = {
    import collection.JavaConverters._
    val imgs = for {
      img <- el.select("img[src]").asScala
      src <- Option(img.attr("src")).filter(_.nonEmpty)
    } yield src

    imgs.map(s => Uri.sanitized(s).resolvedAgainst(base)).toSet
  }

  def rewriteUrls(doc: Document, rewrite: String => String): Document = {
    import collection.JavaConverters._

    for (src  <- doc.select("[src]").asScala; href <- doc.select("[href]").asScala) {
      src.attr("src", rewrite(src.attr("src")))
      href.attr("href", rewrite(href.attr("href")))
    }
    doc
  }

  def rebaseUri(base: Uri): String => String = s => try {
    if (s contains "://") s
    else Uri.sanitized(s).resolvedAgainst(base).toString()
  } catch {
    case NonFatal(e) =>
      e.printStackTrace()
      s
  }

}
