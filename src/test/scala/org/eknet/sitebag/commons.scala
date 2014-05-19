package org.eknet.sitebag

import spray.http._
import scala.concurrent.Future
import akka.actor.{ActorRef, Props, ActorSystem}
import spray.http.HttpResponse
import org.eknet.sitebag.content.Content
import akka.util.ByteString
import org.eknet.sitebag.model.{PageEntry, FullPageEntry}
import scala.util.Random
import java.nio.file.{FileVisitResult, Path, SimpleFileVisitor, Files}
import java.nio.file.attribute.BasicFileAttributes
import java.io.{File, IOException}
import org.eknet.sitebag.lucene._
import org.apache.lucene.index.{Term, IndexWriterConfig, IndexWriter, DirectoryReader}
import org.apache.lucene.store.FSDirectory
import org.apache.lucene.search.{TopScoreDocCollector, IndexSearcher}
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.document._
import org.eknet.sitebag.model.PageEntry
import spray.http.HttpResponse
import org.eknet.sitebag.model.FullPageEntry
import org.eknet.sitebag.model.PageEntry
import spray.http.HttpResponse
import org.eknet.sitebag.model.FullPageEntry

object commons {

  val htmlType = ContentType(MediaTypes.`text/html`, HttpCharsets.`UTF-8`)
  private val random = new Random()

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

  def randomWord = {
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

  def deleteDirectory(path: Path) {
    if (Files.exists(path)) {
      Files.walkFileTree(path, new SimpleFileVisitor[Path] {
        override def visitFile(file: Path, attrs: BasicFileAttributes) = {
          Files.delete(file)
          FileVisitResult.CONTINUE
        }
        override def postVisitDirectory(dir: Path, exc: IOException) = {
          if (exc == null) Files.delete(dir) else throw exc
          FileVisitResult.CONTINUE
        }
      })
    }
  }

  def search[T](index: File, q: QueryMaker)(implicit dr: DocumentReader[T]): Iterable[T] = {
    val reader = DirectoryReader.open(FSDirectory.open(index))
    try {
      val searcher = new IndexSearcher(reader)
      val collector = TopScoreDocCollector.create(10, true)
      searcher.search(q(), collector)
      collector.topDocs().scoreDocs.toVector.map { sdoc =>
        val doc = searcher.doc(sdoc.doc)
        dr.read(doc)
      }.flatten
    } finally {
      reader.close()
    }
  }

  def addToIndex[T](index: File, values: T*)(implicit dw: DocumentWriter[T]) {
    val writer = new IndexWriter(FSDirectory.open(index), new IndexWriterConfig(lucene.luceneVersion, new StandardAnalyzer(lucene.luceneVersion)))
    val docs = values.map(dw.write)
    docs.foreach(writer.addDocument)
    writer.close()
  }

  object persons {
    case class Person(name: String, age: Int, vita: String = "")
    implicit val personHandler = new DocumentConverter[Person] {
      def read(doc: Document) = {
        val name = doc("name").getOrElse("unknown")
        val age = doc.as[Int]("age").getOrElse(-1)
        Some(Person(name, age))
      }

      def write(value: Person) = {
        val doc = new Document()
        doc += value.name.asField("name").indexed.stored.notTokenized
        doc += value.age.asField("age").indexed.stored.notTokenized
        doc += value.vita.asField("vita").indexed.notStored.tokenized
        doc
      }
    }
    implicit val personKey = new TermCreator[Person] {
      def create(value: Person) = new Term("name", value.name)
    }
  }
}
