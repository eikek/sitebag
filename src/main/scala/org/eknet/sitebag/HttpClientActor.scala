package org.eknet.sitebag

import scala.concurrent.duration._
import scala.concurrent.Future
import akka.actor.{ActorRef, Props, Actor, ActorLogging}
import akka.pattern.pipe
import akka.pattern.ask
import akka.util.Timeout
import spray.http._
import spray.client.pipelining._
import org.eknet.sitebag.content.Content
import spray.httpx.encoding.Gzip
import javax.net.ssl.{KeyManager, SSLContext, X509TrustManager}
import java.security.cert
import spray.io.ClientSSLEngineProvider
import akka.io.IO
import org.eknet.sitebag.HttpClientActor.TrustAllSslConfiguration
import spray.can.Http.HostConnectorSetup
import spray.can.Http
import spray.http.HttpResponse
import org.eknet.sitebag.content.ExtractedContent

object HttpClientActor {
  def apply(extrRef: ActorRef) = Props(classOf[HttpClientActor], extrRef)

  trait TrustAllSslConfiguration {
    implicit val sslContext: SSLContext = {
      val tm = new X509TrustManager() {
        def checkClientTrusted(chain: Array[cert.X509Certificate], authType: String) = ()
        def checkServerTrusted(chain: Array[cert.X509Certificate], authType: String) = ()
        def getAcceptedIssuers = Array.empty
      }
      val ctx = SSLContext.getInstance("TLS")
      ctx.init(Array[KeyManager](), Array(tm), null)
      ctx
    }
    implicit val sslEngineProvider: ClientSSLEngineProvider = ClientSSLEngineProvider.default
  }
}
class HttpClientActor(extrRef: ActorRef) extends Actor with ActorLogging with TrustAllSslConfiguration {
  import context.dispatcher
  implicit val timeout: Timeout = 10.seconds

  val settings = SitebagSettings(context.system)

  type ExtractResult = Result[ExtractedContent]

  protected def sendAndReceive: SendReceive = sendReceive

  def extractContent(a: ExtractRequest)(r: HttpResponse): Future[ExtractResult] = {
    r.status match {
      case StatusCodes.Success(_) =>
        (extrRef ? Content(a.url, r)).mapTo[ExtractResult]
      case error =>
        Future.successful(Failure("Error response from web page: "+error))
    }
  }

  private def get(uri: Uri): Future[HttpResponse] = {
    val resChain = sendAndReceive ~> decode(Gzip)
    uri.scheme match {
      case "https" if settings.trustAllSsl =>
        val connectSsl = IO(Http)(context.system) ? HostConnectorSetup(uri.authority.host.address, uri.effectivePort, sslEncryption = true)
        for {
          Http.HostConnectorInfo(hostConnector, _) <- connectSsl
          response <- resChain(Get(uri))
          _ <- hostConnector ? Http.CloseAll
        } yield response
      case _ => resChain(Get(uri))
    }
  }

  def receive = {
    case r: ExtractRequest =>
      val chain: Future[ExtractResult] = get(r.url).flatMap(res => extractContent(r)(res))
      chain pipeTo sender

    case r: FetchContent =>
      def makeContent: HttpResponse => Content = res => Content(r.url, res)
      val chain: Future[Content] = get(r.url).map(makeContent)
      chain pipeTo sender
  }
}
