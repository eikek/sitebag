package org.eknet.sitebag.content

import scala.collection.JavaConverters._
import spray.http.{ContentType, MediaTypes}
import org.jsoup.select.{NodeVisitor, NodeTraversor, Elements}
import org.jsoup.nodes.{TextNode, Node, Element, Document}
import org.eknet.sitebag.utils._

object HtmlExtractor extends Extractor {
  import Html._

  val htmlType = MediaTypes.`text/html`
  val magicValue = 8.5

  val pf: PartialFunction[Content, ExtractedContent] = {
    case c@Content(uri, Some(ContentType(`htmlType`, cset)), data) =>
      val (doc, meta) = HtmlParser.parseContent(c, uri)
      val (main, title) = extract(doc)
      val lang = main.selectParents("[lang]").attrs("lang").headOption
      val bins = main.findBinaries(uri).map { u =>
        if (u.authority.isEmpty) u.resolvedAgainst(uri) else u
      }
      ExtractedContent(c,
        removeWeirdChars(title.toOption.orElse(meta.title.toOption).getOrElse("No title").takeDots(120)),
        removeWeirdChars(main.html()),
        removeWeirdChars(main.last().text().takeDots(180)),
        lang.orElse(meta.language),
        bins)
  }

  private def removeWeirdChars(s: String): String = {
    val invalid = (0 to  32).toSet - 13 - 10 - 32 - 9
    val isValid: Char => Boolean = c => !invalid.contains(c.toInt)
    val buf = new StringBuilder
    for (c <- s; if isValid(c)) {
      buf append c
    }
    buf.toString()
  }

  def isDefinedAt(p: Content) = pf.isDefinedAt(p)
  def apply(p: Content) = pf(p)

  def extract(doc: Document): (Elements, String) = {
    val main = findMainContentElement(doc)
    main.findPathToTags("h1") orElse main.findPathToTags("h2") match {
      case Some((els, title)) => (els, title.toOption.getOrElse(doc.title()))
      case None    => main.asElements -> doc.title()
    }
  }

  @scala.annotation.tailrec
  final def findMainContentElement(el: Element): Element = {
    val ielems = for (cel <- el.children().asScala) yield wordCount(cel)
    val tnodes = for (tel <- el.textNodes().asScala) yield wordCount(tel)
    val worded = (ielems ++ tnodes).filter(_ > 0)
    if (worded.isEmpty) {
      el
    }
    else if (worded.size == 1) {
      ielems.zipWithIndex.collect({ case (c, idx) if c > 0 => idx }).headOption match {
        case Some(index) => findMainContentElement(el.child(index))
        case _           => el
      }
    }
    else {
      val total = wordCount(el)
      val allnodes = ielems ++ tnodes
      val mean = allnodes.sum.toDouble / allnodes.length.toDouble
      val s = math.sqrt(allnodes.map(xi => math.pow(xi - mean, 2)).sum / allnodes.length.toDouble)
      val p = s / total.toDouble * 100.0
      if (p <= magicValue) {
        el
      } else {
        val maxEl = if (ielems.isEmpty) 0 else ielems.max
        if (maxEl == 0) el
        else findMainContentElement(el.child(ielems.indexOf(maxEl)))
      }
    }
  }

  final def wordCount(el: Node): Int = {
    var count = 0
    new NodeTraversor(new NodeVisitor() {
      def head(node: Node, depth: Int) {
        node match {
          case tn: Element =>
            val str = tn.text()
            count += str.trim.length //.split("[\\W\\s]+").filter(_.nonEmpty).length
          case tn: TextNode =>
            count += tn.text().trim.length
          case _ =>
        }
      }
      def tail(node: Node, depth: Int) = ()
    }).traverse(el)
    count
  }
}
