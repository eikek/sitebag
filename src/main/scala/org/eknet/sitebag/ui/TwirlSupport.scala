package org.eknet.sitebag.ui

import spray.httpx.marshalling.ToResponseMarshaller
import spray.http._
import twirl.api.Html
import spray.http.HttpResponse

object TwirlSupport {
  val htmlType = ContentType(MediaTypes.`text/html`, HttpCharsets.`UTF-8`)

  implicit val htmlTemplateMarshall = ToResponseMarshaller[Html] { (templ, ctx) =>
    ctx.marshalTo(HttpResponse(status = StatusCodes.OK, entity = HttpEntity(htmlType, templ.body)))
  }
}
