package org.eknet.sitebag.rest

import spray.routing.{Directives, Route}
import org.eknet.sitebag._
import akka.util.Timeout
import scala.concurrent.ExecutionContext
import akka.actor.{ActorRef, ActorRefFactory}
import org.eknet.sitebag.content.Content
import spray.httpx.marshalling.Marshaller
import spray.http.{ContentTypes, HttpEntity}
import org.eknet.sitebag.GetBinaryById
import org.eknet.sitebag.GetBinaryByUrl
import org.eknet.sitebag.model.Binary

class BinaryHttp (val settings: SitebagSettings, store: ActorRef, refFactory: ActorRefFactory, ec: ExecutionContext, to: Timeout)
  extends Directives with RestDirectives {

  implicit def timeout = to
  implicit def executionContext = ec

  import akka.pattern.ask

  implicit val contentMarshaller = Marshaller[Result[Binary]] { (result, context) =>
    result match {
      case Success(Some(content), _) =>
        val entity = HttpEntity(content.contentType, content.data)
        context.marshalTo(entity)
      case Success(None, _) =>
        context.marshalTo(HttpEntity("Not found."))
      case Failure(_, error) =>
        val e = error.getOrElse(new Exception("Failure getting content"))
        context.handleError(e)
    }
  }


  def route: Route = {
    path(Segment) { id =>
      complete {
        (store ? GetBinaryById(id)).mapTo[Result[Binary]]
      }
    } ~
    parameter("url") { url =>
      complete {
        (store ? GetBinaryByUrl(url)).mapTo[Result[Binary]]
      }
    }
  }
}
