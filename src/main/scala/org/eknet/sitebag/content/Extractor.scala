package org.eknet.sitebag.content

import spray.http.{ContentType, MediaTypes}
import scala.util.Try
import org.eknet.sitebag._
import org.eknet.sitebag.utils._

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
}

object TextplainExtractor extends Extractor {
  val textPlain = MediaTypes.`text/plain`
  val pf: PartialFunction[Content, ExtractedContent] = {
    case c@Content(_, Some(ContentType(`textPlain`, cset)), data) =>
      val extracted = data.decodeString(cset.map(_.value).getOrElse("UTF-8"))
      ExtractedContent(c, "No title", extracted, extracted.takeDots(180), Set.empty)
  }

  def isDefinedAt(p: Content) = pf.isDefinedAt(p)
  def apply(p: Content) = pf(p)
}
