package org.eknet.sitebag.content

import scala.collection.JavaConverters._
import scala.util.control.NonFatal
import org.jsoup.nodes.{Element, Document}
import spray.http.Uri
import org.jsoup.select.{Evaluator, Collector, Elements}
import org.eknet.sitebag.utils._

object Html {

  def rebaseUri(base: Uri): String => String = s => try {
    if (s contains "://") s
    else Uri.sanitized(s).resolvedAgainst(base).toString()
  } catch {
    case NonFatal(e) =>
      e.printStackTrace()
      s
  }

  implicit class ElementHelper(el: Element) {

    def parentOpt = Option(el.parent())
    def previous = Option(el.previousElementSibling())

    def asElements = new Elements(el)

    def findFirst(tagname: String, rest: String*) = {
      @scala.annotation.tailrec
      def loop(names: List[String]): Option[Element] = names match {
        case Nil   => None
        case a::as => el.getElementsByTag(a).single match {
          case r@Some(_) => r
          case _         => loop(as)
        }
      }
      loop((tagname +: rest).toList)
    }

    final def previousDeep: Option[Element] = previous orElse parentOpt.flatMap(_.previousDeep)

    final def findPathToTags(firstTag: String, rest: String*): Option[(Elements, String)] = {
      @scala.annotation.tailrec
      def loop(cursor: Element, coll: List[Element]): Option[(List[Element], String)] = {
        val headline = cursor.findFirst(firstTag, rest: _*)
        headline match {
          case Some(h) => Some((h :: cursor :: coll, h.select("h1,h2").text().trim))
          case _       =>
            val prev = cursor.previousDeep
            prev match {
              case Some(p) => loop(p, cursor :: coll)
              case _       => None
            }
        }
      }

      loop(el, Nil).map({case (els, title) => new Elements(els.asJavaCollection) -> title })
    }

    def rewriteUrls(rewrite: String => String): Element = {
      for (src  <- el.select("[src]").asScala; href <- el.select("[href]").asScala) {
        src.attr("src", rewrite(src.attr("src")))
        href.attr("href", rewrite(href.attr("href")))
      }
      el
    }

    def findBinaries(base: Uri): Set[Uri] = {
      el.asElements.findBinaries(base)
    }

    def selectParents(selector: String) = {
      el.asElements.selectParents(selector)
    }
  }

  implicit class ElementsHelper(els: Elements) {
    def single: Option[Element] = if (els.size() == 1) Some(els.get(0)) else None

    def findBinaries(base: Uri): Set[Uri] = {
      import collection.JavaConverters._
      val imgs = for {
        img <- els.select("img[src]").asScala
        src <- Option(img.attr("src")).filter(_.nonEmpty)
      } yield src

      imgs.map(s => Uri.sanitized(s).resolvedAgainst(base)).toSet
    }

    def attrs(name: String): Iterable[String] = {
      els.asScala.flatMap(_.attr(name).toOption)
    }

    def selectParents(selector: String) = {
      def copyShallow(el: Element): Element = {
        val attrs = el.attributes().clone()
        val name = el.tag()
        val text = el.ownText()
        new Element(name, el.baseUri(), attrs).text(text)
      }
      val pels = els.parents().asScala.map(copyShallow).asJavaCollection
      new Elements(pels).select(selector)
    }
  }

}
