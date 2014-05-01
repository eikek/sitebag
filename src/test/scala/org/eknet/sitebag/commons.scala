package org.eknet.sitebag

import spray.http._
import scala.concurrent.Future
import akka.actor.{ActorRef, Props, ActorSystem}
import spray.http.HttpResponse
import org.eknet.sitebag.content.Content
import akka.util.ByteString
import org.eknet.sitebag.model.{PageEntry, FullPageEntry}
import scala.util.Random

object commons {

  val htmlType = ContentType(MediaTypes.`text/html`, HttpCharsets.`UTF-8`)
  val random = new Random()

  def html(name: String) = {
    val url = getClass.getResource(s"/$name")
    require(url != null, s"Resource '$name' not found")
    io.Source.fromURL(url).getLines().mkString
  }

  def createClient(extrRef: ActorRef, response: HttpResponse)(implicit system: ActorSystem) =
    system.actorOf(Props(new HttpClientActor(extrRef) {
      override protected def sendAndReceive = req => Future.successful(response)
    }))

  def create404Client(extrRef: ActorRef)(implicit system: ActorSystem) =
    createClient(extrRef, HttpResponse(status = StatusCodes.NotFound, entity = "Page not found."))

  def createHtmlClient(extrRef: ActorRef, name: String)(implicit system: ActorSystem) =
    createClient(extrRef, HttpResponse(status = StatusCodes.OK, entity = HttpEntity(htmlType, html(name))))

  private val letters = ('a' to 'z') ++ ('A' to 'Z')
  private def letter = letters(random.nextInt(letters.length))

  private def randomWord = {
    val len = random.nextInt(10) + 7
    Iterator.continually(letter).take(len).mkString
  }

  def newEntry: FullPageEntry = {
    val title = Iterator.fill(3)(randomWord).mkString(" ")
    val text = Iterator.fill(random.nextInt(34)+12)(randomWord).mkString(" ")
    val short = text.substring(0, 20)
    val uri = "http://" + randomWord + ".com/" + randomWord + ".html"
    val entry = PageEntry(title, uri, text, short)
    FullPageEntry(entry, Content(uri, ByteString(text)))
  }
}
