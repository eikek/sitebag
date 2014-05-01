package org.eknet.sitebag.content

import akka.util.ByteString
import spray.http._
import spray.http.HttpEntity.NonEmpty

trait Content {
  def uri: Uri
  def data: ByteString
  def isEmpty: Boolean
  def contentType: Option[ContentType]
  final def asString = contentType.flatMap { ct =>
    if (ct.mediaType.isText) ct.definedCharset.map(cs => data.decodeString(cs.value)).orElse(Some(data.utf8String))
    else None
  }
}

object Content {

  def unapply(c: Content): Option[(Uri, Option[ContentType], ByteString)] =
    Some(c.uri, c.contentType, c.data)

  def apply(uri: Uri, r: HttpResponse): Content = HttpContent(uri, r.entity)

  def apply(uri: Uri, data: ByteString, contentType: Option[ContentType] = None): Content = {
    import org.eknet.sitebag.utils._
    SimpleContent(uri, data, contentType.orElse(contentTypeByExtension(uri)))
  }

  private case class HttpContent(uri: Uri, entity: HttpEntity) extends Content {
    def data = entity.data.toByteString
    lazy val contentType = entity match {
      case NonEmpty(ct, _) => Some(ct)
      case _ => None
    }
    def isEmpty = entity.data.isEmpty
  }

  private case class SimpleContent(uri: Uri, data: ByteString, contentType: Option[ContentType]) extends Content {
    def isEmpty = data.isEmpty
  }
}

case class ExtractedContent(original: Content, title: String, text: String, shortText: String, binaryUrls: Set[Uri])