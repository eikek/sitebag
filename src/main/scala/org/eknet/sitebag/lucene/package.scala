package org.eknet.sitebag

import org.apache.lucene.util.Version
import org.apache.lucene.index.{Term, DirectoryReader}
import org.apache.lucene.search.Query
import org.eknet.sitebag.model.Page
import org.apache.lucene.document.Document
import scala.reflect.ClassTag

package object lucene {

  val luceneVersion = Version.LUCENE_48

  final case class Keyword(s: String) extends AnyVal

  trait WriteMessage extends Serializable
  case class Mod(m: Index => Unit, message: String = "Modification done.") extends WriteMessage
  case object ClearIndex extends WriteMessage

  /** The update command first searches a single document by the given term and then
    * applies the mutation function to it. The modified document is then passed to
    * `updateDocument` with the given term.
    *
    * ''NOTE:'' This only works for documents that completely consist of stored fields! Otherwise
    * non-stored fields are deleted.
    *
    * @param term
    * @param tc
    * @param mutate
    * @tparam T
    */
  case class Update[T](term: T, tc: TermCreator[T], mutate: Document => Unit) extends WriteMessage
  object Update {
    def create[T](term: T, mutate: Document => Unit)(implicit tc: TermCreator[T]): Update[T] =
      Update(term, tc, mutate)
  }

  trait ReadMessage extends Serializable
  case class ReadDirectory[A](r: DirectoryReader => A) extends ReadMessage
  case class QueryDirectory[A](qm: QueryMaker, page: Page, reader: DocumentReader[A]) extends ReadMessage
  object QueryDirectory {
    def create[A](qm: QueryMaker, page: Page)(implicit reader: DocumentReader[A]): QueryDirectory[A] =
      QueryDirectory(qm, page, reader)
  }
  case class FindByTerm[T, A](term: T, reader: DocumentReader[A], tc: TermCreator[T]) extends ReadMessage
  object FindByTerm {
    def create[T, A](term: T)(implicit dr: DocumentReader[A], tc: TermCreator[T]): FindByTerm[T, A] =
      FindByTerm(term, dr, tc)
  }


  implicit val tupleTerm = new TermCreator[(String, String)] {
    def create(value: (String, String)) = new Term(value._1, value._2)
  }

  implicit val documentHandler = new DocumentConverter[Document] {
    def read(doc: Document) = Some(doc)
    def write(value: Document) = value
  }

  implicit class DocumentOps(doc: Document) {

    def apply(name: String): Option[String] = Option(doc.get(name))
    def values(name: String): Vector[String] = doc.getValues(name).toVector
    def as[T](name: String): Option[T] = {
      Option(doc.getField(name)).map(_.numericValue().asInstanceOf[T])
    }

    def +=[A] (f: DocField[A]): Document = {
      doc add f
      doc
    }
  }
  implicit class StringOps(s: String) {
    def asField(name: String) = DocField(name, s)
  }
  implicit class IntOps(i: Int) {
    def asField(name: String) = DocField(name, i)
  }
  implicit class LongOps(l: Long) {
    def asField(name: String) = DocField(name, l)
  }
  implicit class BoolOps(b: Boolean) {
    def asField(name: String) = DocField(name, b)
  }
}
