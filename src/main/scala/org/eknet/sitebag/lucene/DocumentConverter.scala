package org.eknet.sitebag.lucene

import org.apache.lucene.document.{Field, Document}
import org.apache.lucene.index.Term

trait DocumentConverter[T] extends DocumentReader[T] with DocumentWriter[T]

trait DocumentWriter[T] {
  def write(value: T): Document
}
trait DocumentReader[T] {
  def read(doc: Document): Option[T]
}

trait TermCreator[T]  {
  def create(value: T): Term
}
