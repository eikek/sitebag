package org.eknet.sitebag.rest

import org.scalatest.{MustMatchers, FunSuite}
import spray.httpx.unmarshalling.Unmarshaller
import spray.httpx.SprayJsonSupport
import spray.http.{Uri, HttpEntity, MediaTypes, ContentType}

class UnmarshallerTest extends FunSuite with MustMatchers {

  val jsonType = ContentType(MediaTypes.`application/json`)
  val formType = ContentType(MediaTypes.`application/x-www-form-urlencoded`)

  test("unmarshall json and www-url-form-encoded") {
    import spray.json._
    import JsonProtocol._
    import FormUnmarshaller._
    implicit val taginputFormdata = formDataDelegate[TagInput](TagInput.fromFields)

    val jsonSource = """{ "tags": ["a","b","c"] }"""
    val formSource = "tag=a&tag=b&tag=c" :: "tags=a,b,c" :: "tag=a&tags=b,c" :: Nil
    val expected = TagInput(Set("a", "b", "c"))

    //for completeness...
    JsonParser(jsonSource).convertTo[TagInput] must be (expected)

    //check form
    formSource foreach { src =>
      taginputFormdata(HttpEntity(formType, src)) must be (Right(expected))
    }

    //check either
    val either = CommonDirectives.unmarshalFormOrJson(taginputFormat, taginputFormdata)
    either.apply(HttpEntity(jsonType, jsonSource)) must be (Right(expected))
    formSource foreach { src =>
      either(HttpEntity(formType, src)) must be (Right(expected))
    }
  }

  test("make sure the toString() works like i use it") {
    val uri1 = Uri("http://localhost:9911")
    uri1.authority.toString() must be ("//localhost:9911")

    val uri2 = Uri("https://myhost.com")
    uri2.authority.toString() must be ("//myhost.com")
  }
}
