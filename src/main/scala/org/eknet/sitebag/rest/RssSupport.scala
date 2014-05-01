package org.eknet.sitebag.rest

import scala.xml.PrettyPrinter
import spray.http._
import spray.http.HttpResponse
import spray.httpx.marshalling.ToResponseMarshaller
import porter.model.Ident
import org.eknet.sitebag.{Failure, Success, Result}
import org.eknet.sitebag.model.PageEntry

object RssSupport {

  type RssMarshall[T] = T => xml.Node


  class EntryRss(uriMaker: PageEntry => Uri) extends RssMarshall[PageEntry] {
    def apply(entry: PageEntry) = {
      val url = uriMaker(entry)

      <item>
        <title>{ entry.title }</title>
        <link>{ url.toString() }</link>
        <guid>{ url.toString() }</guid>
        <pubDate>{ entry.created.toRfc1123DateTimeString }</pubDate>
        <description>{ entry.content }</description>
        { entry.tags.map(t => <category>{ t.name }</category> ) }
      </item>
    }
  }

  def toRss(title: String, description: String, link: Uri, items: Seq[PageEntry])(uriMaker: PageEntry => Uri): xml.NodeSeq = {
    val entryRss = new EntryRss(uriMaker)

    <rss version="2.0" xmlns:atom="http://www.w3.org/2005/Atom">
      <channel>
        <title>{ title }</title>
        <description>{ description }</description>
        <link>{ link.toString() }</link>
        <atom:link href={ link.toString() } rel="self" type="application/rss+xml" />
        <generator>sitebag</generator>
        { items.map(entryRss) }
      </channel>
    </rss>
  }

  def mapRss(url: Uri, user: Ident, search: EntrySearch, result: Result[List[PageEntry]])(uriMaker: PageEntry => Uri): Result[xml.NodeSeq] = {
    val title = s"${user.name}'s sitebag feed: ${search.tag.tags.map(_.name).mkString(", ")}"
    val descr = s"sitebag - elementy by tags"
    result mapmap { list => toRss(title, descr, url, list)(uriMaker) }
  }

  def rssEntity(xmldata: Result[xml.NodeSeq]) = {
    val printer = new PrettyPrinter(80, 2)
    val rss = MediaTypes.`text/xml`
    xmldata match {
      case Success(Some(node), _) =>
        HttpResponse(
          status = StatusCodes.OK,
          entity = HttpEntity(ContentType(rss, HttpCharsets.`UTF-8`), """<?xml version="1.0" encoding="utf-8"?>""" +printer.formatNodes(node)))
      case Success(None, _) =>
        HttpResponse(status = StatusCodes.NotFound, entity = HttpEntity("RSS feed resource not found."))
      case Failure(msg, ex) =>
        HttpResponse(status = StatusCodes.InternalServerError, entity = HttpEntity(msg))
    }
  }
  implicit val rssMarshaller = ToResponseMarshaller[Result[xml.NodeSeq]] {
    (res, ctx) => ctx.marshalTo(rssEntity(res))
  }
}
