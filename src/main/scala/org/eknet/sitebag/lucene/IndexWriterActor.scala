package org.eknet.sitebag.lucene

import akka.actor.{ReceiveTimeout, Props, Actor, ActorLogging}
import org.apache.lucene.store.{FSDirectory, Directory}
import org.apache.lucene.index.{IndexWriterConfig, IndexWriter}
import java.io.File
import java.nio.file.Path
import org.apache.lucene.analysis.standard.StandardAnalyzer
import scala.concurrent.Future
import org.eknet.sitebag.{SitebagSettings, Failure, Success}
import java.util.concurrent.atomic.AtomicInteger

/**
 * Manages a [[org.apache.lucene.index.IndexWriter]] for a given index. It
 * executes modifications concurrently and closes the writer on a configured
 * idle timeout.
 *
 * @param directory
 */
class IndexWriterActor(directory: Directory) extends Actor with ActorLogging {
  import context.dispatcher
  import akka.pattern.pipe

  private val settings = SitebagSettings(context.system)
  private val analyzer = new StandardAnalyzer(luceneVersion)
  private var writer: Option[IndexWriter] = None
  private val writerRef = new AtomicInteger(0)

  context.setReceiveTimeout(settings.indexReceiveTimeout)

  private def nextWriter = {
    def makeWriter = new IndexWriter(directory, new IndexWriterConfig(luceneVersion, analyzer))
    writer getOrElse {
      writer = Some(makeWriter)
      writer.get
    }
  }

  def receive = {
    case ReceiveTimeout =>
      if (writerRef.get() == 0) {
        closeCurrentWriter()
      }

    case Mod(mutate, msg) =>
      val w = nextWriter //IndexWriter is thread-safe, only one allowed per directory
      writerRef.incrementAndGet()
      val f = Future {
        mutate(Index(w, analyzer))
        w.commit()
        Success(msg)
      }.recover({ case ex => Failure(ex) })
      f onComplete (_ => writerRef.decrementAndGet())
      f pipeTo sender
  }


  override def postStop() = {
    closeCurrentWriter()
    super.postStop()
  }

  private def closeCurrentWriter() {
    writer.map(w => {
      log.info("Close current index writer")
      w.close()
    })
    writer = None
  }
}
object IndexWriterActor {
  def apply(directory: Directory): Props = Props(classOf[IndexWriterActor], directory)
  def apply(directory: File): Props = apply(FSDirectory.open(directory))
  def apply(directory: Path): Props = apply(directory.toFile)
}
