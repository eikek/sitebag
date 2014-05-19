package org.eknet.sitebag.lucene

import scala.concurrent.duration._
import scala.concurrent.Future
import akka.actor._
import akka.pattern.ask
import akka.pattern.pipe
import akka.util.Timeout
import java.io.{IOException, File}
import org.apache.lucene.store.FSDirectory
import org.eknet.sitebag.{Ack, Result, Failure, Success}
import java.nio.file.{FileVisitResult, Path, SimpleFileVisitor, Files}
import java.nio.file.attribute.BasicFileAttributes
import akka.actor.Terminated
import org.apache.lucene.document.Document

/**
 * Combines the `IndexWriter` and `IndexReader` and manages one index directory.
 *
 * @param indexDir
 */
class IndexActor(indexDir: File) extends Actor with ActorLogging {
  import context.dispatcher

  private implicit val timeout: Timeout = 5.seconds

  private var directory: FSDirectory = _
  private var readerRef: ActorRef = _
  private var writerRef: ActorRef = _

  def receive = ready

  def ready: Receive = {
    case ClearIndex =>
      context.become(clearing(sender))
      context.children.foreach(context.stop)

    case Update(tval, tc, mutate) =>
      val find = (readerRef ? FindByTerm(tval, documentHandler, tc)).mapTo[Result[Iterable[Document]]]
      val mod = find.flatMap {
        case Success(docs, _) =>
          docs.getOrElse(Nil).toList match {
            case doc :: Nil =>
              mutate(doc)
              implicit val docterm = new TermCreator[Document] {
                def create(value: Document) = tc.create(tval)
              }
              (writerRef ? Mod(_.updateDocument(doc))).mapTo[Ack]
            case Nil =>
              Future.successful(Failure("No documents to modify."))
            case more =>
              Future.successful(Failure(s"Too many documents: ${more.size}. Update only works with one."))
          }
        case f: Failure =>
          Future.successful(f)
      }
      mod pipeTo sender

    case wm: WriteMessage => writerRef forward wm
    case rm: ReadMessage => readerRef forward rm
  }

  def clearing(client: ActorRef, refsLeft: Int = 2, queue: List[(ActorRef, WriteMessage)] = Nil): Receive = {
    case rm: ReadMessage => sender ! Failure("Index is currently being cleared.")

    case wm: WriteMessage =>
      context.become(clearing(client, refsLeft, (sender -> wm) :: queue))

    case Terminated(ref) if refsLeft == 1 =>
      postStop()
      deleteDirectory()
      preStart()
      context.become(ready)
      for ((s, m) <- queue) {
        self.tell(m, s)
      }
      client ! Success("Index cleared.")

    case Terminated(ref) if refsLeft > 1 =>
      context.become(clearing(client, refsLeft -1))
  }

  override def preStart() = {
    this.directory = FSDirectory.open(indexDir)
    this.readerRef = context.watch(context.actorOf(IndexReaderActor(directory)))
    this.writerRef = context.watch(context.actorOf(IndexWriterActor(directory)))
  }

  override def postStop() = {
    directory.close()
    directory = null
  }

  private def deleteDirectory() {
    if (Files.exists(indexDir.toPath)) {
      Files.walkFileTree(indexDir.toPath, new SimpleFileVisitor[Path] {
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
}

object IndexActor {
  def apply(indexDir: File): Props = Props(classOf[IndexActor], indexDir)
  def apply(indexDir: Path): Props = apply(indexDir.toFile)
}