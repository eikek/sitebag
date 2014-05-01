package org.eknet.sitebag.content

import org.scalatest.{Matchers, FunSuite}
import java.nio.charset.Charset
import akka.util.ByteString
import spray.http.ContentTypes

class HtmlParserTest extends FunSuite with Matchers {

  test("find charset") {
    val htmlstr = <html><head><meta http-equiv="Content-Type" content="text/html; charset=windows-1252"></meta></head></html>
    val (doc, meta) = HtmlParser.parseString(htmlstr.toString())
    assert(meta.charset === Some(Charset.forName("windows-1252")))
  }

  test("use charset from meta") {
    val htmlStr = "<html><head><meta charset=\"iso8859-1\"></meta><title>x</title></head></html>"
      .getBytes.map(b => if (b.toChar == 'x') (-36).toByte else b)

    val content = Content("http://test", ByteString(htmlStr), Some(ContentTypes.`text/plain(UTF-8)`))
    val (doc, meta) = HtmlParser.parseContent(content, "")
    assert(meta.charset == Some(Charset.forName("iso8859-1")))
    assert(meta.title === "Ü")
  }

}
