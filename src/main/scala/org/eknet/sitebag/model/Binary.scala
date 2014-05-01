package org.eknet.sitebag.model

import spray.http._
import scala.concurrent.{ExecutionContext, Future}
import java.net.URL
import java.security.MessageDigest
import akka.util.ByteString
import javax.xml.bind.DatatypeConverter
import org.eknet.sitebag.content.Content

case class Binary(id: String, url: Uri, data: ByteString, md5: String, contentType: ContentType, created: DateTime = DateTime.now) extends Serializable

object Binary {
  val octetstream = ContentTypes.`application/octet-stream`

  def apply(c: Content): Binary = {
    val md5 = makeMd5(c.data)
    Binary(md5, c.uri, c.data, md5, c.contentType.getOrElse(octetstream))
  }

  def load(uri: Uri)(implicit ec: ExecutionContext): Future[Binary] = Future {
    import org.eknet.sitebag.utils._
    val conn = new URL(uri.toString()).openConnection()
    val contentType = getContentTypeWithFallback(Option(conn.getContentType), uri)

    val in = conn.getInputStream
    val data = {
      val datab = ByteString.newBuilder
      Iterator.continually(in.read()).takeWhile(_ >= 0).foreach(b => datab += b.toByte)
      datab.result()
    }
    val md5 = makeMd5(data)
    Binary(md5, uri, data, md5, contentType)
  }

  private def makeMd5(data: ByteString): String = {
    val md = MessageDigest.getInstance("MD5")
    md.update(data.toArray)
    DatatypeConverter.printHexBinary(md.digest()).toLowerCase
  }
}