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
      log.info("Index is being cleared!")
      context.setReceiveTimeout(2.minutes)
      context.become(clearing(sender))
      context.children.foreach(ref ⇒ ref ! Shutdown)

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

  def clearing(client: ActorRef, refsLeft: Int = 2, queue: List[(ActorRef, WriteMessage)] = Nil, queueSize: Int = 0): Receive = {
    case rm: ReadMessage => sender ! Failure("Index is currently being cleared.")

    case wm: WriteMessage =>
      if (queueSize > 5000) {
        log.error("Clearing index cancelled, too many commands received while waiting for indexes to become idle.")
        client ! Failure("Clearing index cancelled due to too many concurrent commands")
        context.setReceiveTimeout(Duration.Undefined)
        context.become(ready)
        queue foreach { case (s,m) ⇒ self.tell(m, s) }
      } else {
        context.become(clearing(client, refsLeft, (sender -> wm) :: queue, queueSize +1))
      }

    case Terminated(ref) if refsLeft == 1 =>
      postStop()
      deleteDirectory()
      preStart()
      context.setReceiveTimeout(Duration.Undefined)
      context.become(ready)
      log.info(s"Index cleared! Releasing $queueSize commands")
      queue foreach { case (s,m) ⇒ self.tell(m, s) }
      client ! Success("Index cleared.")

    case Terminated(ref) if refsLeft > 1 =>
      context.become(clearing(client, refsLeft -1))

    case ReceiveTimeout ⇒
      val message = "Timeout clearing index while waiting for current indexes to become idle"
      log.error(message)
      client ! Failure(message)
      context.setReceiveTimeout(Duration.Undefined)
      context.become(ready)
      queue foreach { case (s,m) ⇒ self.tell(m, s) }
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
