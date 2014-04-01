package org.eknet.sitebag.rest

import spray.routing._
import spray.httpx.unmarshalling._
import spray.httpx.marshalling.ToResponseMarshaller
import spray.json.RootJsonFormat
import spray.httpx.SprayJsonSupport

trait CommonDirectives extends Directives with FormConversions {

  def handle[A, B](f: A => B)(implicit json: RootJsonFormat[A], fdm: FormUnmarshaller[A], m: ToResponseMarshaller[B]): Route = {
    val either = Unmarshaller.oneOf(SprayJsonSupport.sprayJsonUnmarshaller(json), fdm)
    implicit val unm = UnmarshallerLifting.fromRequestUnmarshaller(
      UnmarshallerLifting.fromMessageUnmarshaller(either))
    handleWith(f)
  }


}
