package org.eknet.sitebag.content

import java.nio.charset.Charset
import scala.util.Try
import org.jsoup.nodes.Document
import org.jsoup.Jsoup
import org.jsoup.safety.{Cleaner, Whitelist}
import spray.http.Uri
import org.eknet.sitebag.utils._

object HtmlParser {
  import Html._

  def parseString(html: String, baseUri: Uri = Uri.Empty): (Document, HtmlMeta) = {
    val htmlDoc = Jsoup.parse(html, baseUri.toString())
    val title = htmlDoc.title()
    val doc = clean(htmlDoc)
    val lang = htmlDoc.select("[lang]").attrs("lang").headOption
    doc.rewriteUrls(rebaseUri(baseUri))
    (doc, HtmlMeta(findCharset(htmlDoc), lang, title))
  }

  def parseContent(content: Content, baseUri: Uri = Uri.Empty): (Document, HtmlMeta) = {
    val cs = content.contentType.flatMap(_.definedCharset.map(_.nioCharset))
      .getOrElse(io.Codec.UTF8.charSet)

    val (doc, meta) = parseString(content.data.decodeString(cs.name()), baseUri)
    meta.charset match {
      case Some(ncs) if ncs != cs =>
        parseString(content.data.decodeString(ncs.name()), baseUri)
      case _ =>
        (doc, meta)
    }
  }

  def findCharset(doc: Document): Option[Charset] = {
    lazy val metaCharset = doc.head().select("meta[charset]").attr("charset").toOption.flatMap(makeCharset)
    lazy val metaEquivCs = doc.head().select("meta[http-equiv*=content-type]").attr("charset").toOption.flatMap(makeCharset)
    lazy val metaEquiv = doc.head().select("meta[http-equiv*=content-type]").attr("content").toOption
      .flatMap(parseContentType).withFilter(_.isCharsetDefined).map(_.charset.nioCharset)

    metaCharset orElse metaEquivCs orElse metaEquiv
  }

  private def makeCharset(s: String): Option[Charset] =
    Try(Charset.forName(s)).toOption

  private def clean(htmlDoc: Document): Document = {
    import collection.JavaConverters._
    val contentEls = "p" :: "h1" :: "h2" :: "h3" :: "h4" :: "img" :: Nil
    val containerEls = "div" :: "span" :: Nil

    val cleaner = new Cleaner(Whitelist.relaxed().addAttributes(":all", "lang", "xml:lang"))
    val doc = cleaner.clean(htmlDoc)

    doc.select("script").remove()
    doc.select("form").remove()
    doc.select("style").remove()
    doc.select("link").remove()
    doc.getElementsByAttribute("style").removeAttr("style")
    doc.getElementsByAttribute("class").removeAttr("class")

    //remove all container elements that do not have content children
    for (cel <- doc.select(containerEls.mkString(",")).asScala) {
      val inner = cel.select(contentEls.mkString(","))
      if (inner.isEmpty && !cel.hasText) {
        cel.remove()
      }
    }
    for (pcode <- doc.select("pre>code").asScala) {
      pcode.text(pcode.text())
    }
    doc
  }
}
