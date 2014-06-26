package org.eknet.sitebag.content

import org.eknet.sitebag._
import org.eknet.sitebag.utils._
import scala.util.Try
import spray.http.ContentTypes
import spray.http.{ ContentType, MediaTypes, Uri }

/**
 * Extracts the main text content of response object.
 */
trait Extractor extends PartialFunction[Content, ExtractedContent]

object Extractor {

  def extract(content: Content, extractor: Extractor): Result[ExtractedContent] = {
    if (content.isEmpty) Failure(new Exception(s"The response is empty; there is no content"))
    else Try(extractor(content)) match {
      case scala.util.Success(extract) => Success(extract)
      case scala.util.Failure(ex) => Failure(new Exception(s"Unable to extract content", ex))
    }
  }

  def apply(f: PartialFunction[Content, ExtractedContent]): Extractor = new Extractor {
    def isDefinedAt(x: Content) = f.isDefinedAt(x)
    def apply(v1: Content) = f(v1)
  }

  def combine(first: Extractor, rest: Extractor*): Extractor = {
    val pf = (first +: rest).reduce[PartialFunction[Content, ExtractedContent]](_ orElse _)
    Extractor(pf)
  }

  def combine(extrs: Seq[Extractor]): Extractor =
    Extractor(extrs.reduce[PartialFunction[Content, ExtractedContent]](_ orElse _))

  /**
   * Can be used as a fallback that throws some more informative
   * exception than the default match error
   */
  val errorFallback = Extractor {
    case content =>
      sys.error("No content extractor available.")
  }

  /**
    * An extractor that generates a html message, that it was not able
    * to extract anything. Use this to save the original document anyways,
    * even without having any extracted content.
    */
  val noextraction = Extractor {
    case content ⇒
      import org.eknet.sitebag.ui.html

      val ct = content.contentType.getOrElse(ContentTypes.`application/octet-stream`)
      val short = s"No content extractor for $ct data available."
      val file = if (content.uri.path.isEmpty) content.uri.toString else content.uri.path.reverse.head.toString
      val title = s"$file: no content extractor"
      val text = html.nocontent(content).body
      ExtractedContent(content, title, text, short, None, Set.empty)
  }
}

object TextplainExtractor extends Extractor {
  val textPlain = MediaTypes.`text/plain`
  val pf: PartialFunction[Content, ExtractedContent] = {
    case c @ Content(uri, Some(ContentType(`textPlain`, cset)), data) =>
      val extracted = data.decodeString(cset.map(_.value).getOrElse("UTF-8"))
      val title = uri.path match {
        case Uri.Path.Empty => uri.toString
        case path => path.reverse.head.toString
      }
      ExtractedContent(c, title, addMinimalHtml(extracted), extracted.takeDots(180), None, Set.empty)
  }

  private object MultiLine {
    def unapply(s: String): Option[String] =
      if (s.contains("\n") || s.contains("\r\n")) Some(s.trim) else None
  }
  private object Heading {
    val headregexp = """^(\d+(\.\d+)*).*""".r
    def unapply(s: String): Option[(Int, String)] = s.trim match {
      case headregexp(level, _) => Some((level.split('.').length, s.trim))
      case _ => None
    }
  }

  /**
   * Inspects the string and adds some simple html tags around
   * paragraphs and headlines.
   *
   * Single lines with a previous and following empty line are
   * recognized as headlines if they start with a numbering scheme
   * like `1.2`.
   */
  def addMinimalHtml(text: String): String = {
    val normed = "\n" + text.trim
    val html = normed.split("\r\n\r\n|\n\n") map {
      case Heading((level, line)) => s"<h$level>$line</h$level>"
      case MultiLine(line) => s"<p>$line</p>"
      case line => s"<p>$line</p>"
    }
    html.mkString("\n\n")
  }

  def isDefinedAt(p: Content) = pf.isDefinedAt(p)
  def apply(p: Content) = pf(p)
}
