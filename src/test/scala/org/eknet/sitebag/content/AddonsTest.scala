package org.eknet.sitebag.content

import org.scalatest.{Matchers, FunSuite}
import spray.http.Uri
import org.eknet.sitebag.utils._
import java.nio.charset.Charset

class AddonsTest extends FunSuite with Matchers {

  test("uri sanitizing") {
    Uri.sanitized("http://localhost/a in valid url") should be (Uri("http://localhost/a%20in%20valid%20url"))
    Uri.sanitized("http://my.com/a\npath%20invlaid/test(me).html") should be (Uri("http://my.com/a%0Apath%20invlaid/test(me).html"))
    Uri.sanitized("http://my.com/gfräckel/straße2.pdf") should be (Uri("http://my.com/gfr%C3%A4ckel/stra%C3%9Fe2.pdf"))
    Uri.sanitized("ü") should be (Uri("%C3%BC"))

    Uri.sanitized("titel_hg%202014-2_klein.jpg") should be (Uri("titel_hg%202014-2_klein.jpg"))
    Uri.sanitized("http://my.com/gfr%C3%A4ckel/stra%C3%9Fe2.pdf") should be (Uri("http://my.com/gfr%C3%A4ckel/stra%C3%9Fe2.pdf"))

    Uri.sanitized("http://host/?share=a;x=y;z=1") should be (Uri("http://host/?share=a;x%3Dy;z%3D1"))
  }

  test("parse content type") {
    val win1252 = "text/html; charset=windows-1252"
    assert(parseContentType(win1252).get.charset.nioCharset === Charset.forName("windows-1252"))
  }

}
