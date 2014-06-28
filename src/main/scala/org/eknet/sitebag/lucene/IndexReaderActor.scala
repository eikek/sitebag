package org.eknet.sitebag.lucene

import akka.actor.{Props, ReceiveTimeout, ActorLogging, Actor, PoisonPill}
import org.apache.lucene.store.{FSDirectory, Directory}
import org.apache.lucene.index.DirectoryReader
import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.Future
import org.eknet.sitebag.{SitebagSettings, Failure, Success}
import scala.util.control.NonFatal
import java.io.File
import java.nio.file.Path
import org.eknet.sitebag.model.Page
import org.apache.lucene.search._
import org.eknet.sitebag.lucene.IndexReaderActor.QueryAction
import scala.util.Try

/**
 * Manages a [[org.apache.lucene.index.IndexReader]] for a given index. The
 * index reader is closed after a specific idle timeout elapses.
 *
 * @param directory
 */
class IndexReaderActor(directory: Directory) extends Actor with ActorLogging with IndexClosing {
  import context.dispatcher
  import akka.pattern.pipe

  private val settings = SitebagSettings(context.system)
  context.setReceiveTimeout(settings.indexReceiveTimeout)

  private var reader: Option[DirectoryReader] = None
  private val readerRef = new AtomicInteger(0)

  private def nextReader: DirectoryReader = {
    def open(old: DirectoryReader) =
      if (old.getRefCount <= 0) None
      else Option(DirectoryReader.openIfChanged(old)).map(nr => { old.close(); nr }).orElse(Some(old))

    reader.flatMap(open).getOrElse {
      val nr = DirectoryReader.open(directory)
      reader = Some(nr)
      nr
    }
  }

  def receive = {
    case Shutdown ⇒
      context.become(shuttingdown(readerRef))
      self ! IndexClosing.Check

    case ReceiveTimeout =>
      if (readerRef.get() == 0) {
        closeCurrentReader()
      }

    case ReadDirectory(read) =>
      val r = nextReader
      readerRef.incrementAndGet()
      val f = Future { Success(read(r)) } recover { case NonFatal(ex) => Failure(ex) }
      f onComplete (_ => readerRef.decrementAndGet())
      f pipeTo sender

    case qd: QueryDirectory[_] =>
      Try(qd.qm()) match {
        case scala.util.Success(q) =>
          self forward ReadDirectory(QueryAction(q, qd.page, qd.reader))
        case scala.util.Failure(ex) =>
          sender ! Failure(ex)
      }

    case ft@ FindByTerm(tval, dr, tc) =>
      val q = new TermQuery(tc.create(tval))
      self forward ReadDirectory(QueryAction(q, Page.one, dr))
  }

  override def postStop() = {
    closeCurrentReader()
    super.postStop()
  }

  private def closeCurrentReader() {
    reader.map(r => {
      val name = directory match {
        case fsd: FSDirectory => " for " + fsd.getDirectory.getName
        case _ => ""
      }
      log.info("Closing index reader" + name)
      r.close()
    })
    reader = None
  }
}

object IndexReaderActor {
  def apply(directory: Directory): Props = Props(classOf[IndexReaderActor], directory)
  def apply(directory: File): Props = apply(FSDirectory.open(directory))
  def apply(directory: Path): Props = apply(directory.toFile)

  private case class QueryAction[A](q: Query, page: Page, reader: DocumentReader[A]) extends (DirectoryReader => Iterable[A]) with Serializable {
    def apply(dirReader: DirectoryReader) = {
      val searcher = new IndexSearcher(dirReader)
      val collector = TopScoreDocCollector.create(page.maxCount, true)
      searcher.search(q, collector)
      collector.topDocs().scoreDocs.drop(page.startCount).toVector.map { sdoc =>
        val doc = searcher.doc(sdoc.doc)
        reader.read(doc)
      }.flatten
    }
  }
}
