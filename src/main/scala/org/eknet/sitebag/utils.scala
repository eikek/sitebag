package org.eknet.sitebag

import spray.http._
import spray.http.Uri.Path
import porter.util.Hash
import scala.util.Try

object utils {

  def parseContentType(ct: String): Option[ContentType] = {
    val islash = ct.indexOf('/')
    val (m1, s1) = if (islash > 0) ct.substring(0, islash).trim -> ct.substring(islash+1).trim
    else ct -> ct
    val isemi = s1.indexOf(';')
    val (m, s, c) = if (isemi > 0) (m1.trim, s1.substring(0, isemi).trim, s1.substring(isemi + 1).trim)
    else (m1.trim, s1.trim, "")

    val cs = if (c.nonEmpty) Try(c.substring(8).trim).toOption.flatMap(HttpCharset.custom(_)) else None
    val mt = MediaTypes.getForKey(m -> s)
    mt.map(m => ContentType(m, cs))
  }

  def contentTypeByExtension(uri: Uri): Option[ContentType] = {
    val fname = uri.filename
    val i = fname.indexOf('.')
    if (i > 0) MediaTypes.forExtension(fname.substring(i+1)).map(ContentType.apply)
    else None
  }

  def getContentTypeWithFallback(str: Option[String], uri: Uri) = {
    str.flatMap(parseContentType)
      .orElse(contentTypeByExtension(uri))
      .getOrElse(ContentTypes.`application/octet-stream`)
  }

  implicit class ByteAdds(b: Byte) {
    def isHexChar = (b>='0' && b<='9') || (b>='A' && b<='F') || (b>='a' && b<='f')
    def percentageEncoded = "%%%02X" format (b & 0xFF)
    def percentageEncode(valid: Set[Byte]): String =
      if (valid contains b) b.asInstanceOf[Char].toString else b.percentageEncoded
  }

  implicit class StringOps(s: String) {
    def toOption = if (s.trim.nonEmpty) Some(s.trim) else None

    def takeDots(max: Int) = {
      s.length match {
        case l if l <= max => s
        case l             => s.take(max-3) + "..."
      }
    }

    def percentageEncoded(validBytes: Set[Byte] = validUriChars): String = {
      def hexSuffix(a: Array[Byte]) = a.length == 3 && a(1).isHexChar && a(2).isHexChar
      if (s.isEmpty) s
      else {
        val bytes = s.getBytes
        val pass1 = bytes.sliding(3).map {
          win =>
            win(0) match {
              case '%' => if (hexSuffix(win)) '%' else '%'.toByte.percentageEncoded
              case c => c.percentageEncode(validBytes)
            }
        }.mkString
        //the last chars
        if (bytes.length == 2) pass1 + bytes.takeRight(1)(0).percentageEncode(validBytes)
          else if (bytes.length == 1) pass1
          else pass1 + bytes.takeRight(2).map(_.percentageEncode(validBytes)).reduce(_ + _)
      }
    }
  }

  implicit class UriAdds(uri: Uri) {
    def filename: String = {
      uri.path match {
        case Path.Empty => Hash.md5String(uri.toString())
        case p => p.reverse.head.toString
      }
    }
  }

  private val validUriChars =
    ("ABCDEFGHIJKLMNOPQRSTUVWXYZ" +
      "abcdefghijklmnopqrstuvwxyz" +
      "0123456789" +
      "-._~:/?#[]@!$&'()*+,;=").toSet.map((c: Char) => c.toByte)

  implicit class UriStatics(val uri: Uri.type ) extends AnyVal {

    /**
     * Converts a string into an uri by replacing invalid
     * chars by the "percentage notation".
     *
     * @param uriString
     * @return
     */
    def sanitized(uriString: String): Uri = {
      if (uriString.isEmpty) Uri.Empty
      else {
        val nextUri = uriString.percentageEncoded(validUriChars)
        nextUri.indexOf('?') match {
          case x if x > 0 =>
            val qp = nextUri.substring(x+1).split('&').toList.map { s=>
              s.split("=", 2).toList match {
                case a::b::Nil => a +"="+ b.flatMap(c => c.toByte.percentageEncode(validUriChars - '='.toByte))
                case a::tail => a +"="
              }
            }

            nextUri.substring(0, x) + "?" + qp.mkString("&")
          case _ => nextUri
        }
      }
    }
  }
}
