package org.eknet.sitebag.lucene

import org.apache.lucene.index.IndexWriter
import org.apache.lucene.analysis.Analyzer
import org.apache.lucene.analysis.standard.StandardAnalyzer

trait Index {

  def addDocument[T](value1: T, values: T*)(implicit writer: DocumentWriter[T])
  def addDocument[T](values: Iterable[T])(implicit writer: DocumentWriter[T])

  def updateDocument[T](value1: T, values: T*)(implicit writer: DocumentWriter[T], tc: TermCreator[T])
  def updateDocument[T](values: Iterable[T])(implicit writer: DocumentWriter[T], tc: TermCreator[T])

  def deleteDocument[T](id: T, more: T*)(implicit tc: TermCreator[T])
  def deleteDocument[T](ids: Iterable[T])(implicit tc: TermCreator[T])

  def withAnalyzer(a: Analyzer): Index
  def withStandardAnalyzer = withAnalyzer(new StandardAnalyzer())
}

object Index {

  def apply(writer: IndexWriter, analyzer: Analyzer): Index = new IndexImpl(writer, analyzer)

  private final class IndexImpl(iw: IndexWriter, analyzer: Analyzer) extends Index {

    def withAnalyzer(a: Analyzer) = new IndexImpl(iw, a)

    def addDocument[T](value1: T, values: T*)(implicit writer: DocumentWriter[T]) = {
      addDocument(value1 +: values)
    }
    def addDocument[T](values: Iterable[T])(implicit writer: DocumentWriter[T]) = {
      val docs = values.map(writer.write)
      docs.foreach(iw.addDocument(_, analyzer))
    }

    def updateDocument[T](value1: T, values: T*)(implicit writer: DocumentWriter[T], tc: TermCreator[T]) = {
      updateDocument(value1 +: values)
    }
    def updateDocument[T](values: Iterable[T])(implicit writer: DocumentWriter[T], tc: TermCreator[T]) = {
      val docs = values.map(v => v -> writer.write(v))
      docs foreach { case (v, doc) =>
        val term = tc.create(v)
        iw.updateDocument(term, doc, analyzer)
      }
    }


    def deleteDocument[T](id: T, more: T*)(implicit tc: TermCreator[T]) = {
      deleteDocument(id +: more)
    }

    def deleteDocument[T](ids: Iterable[T])(implicit tc: TermCreator[T]) = {
      val terms = ids.map(tc.create)
      iw.deleteDocuments(terms.toSeq: _*)
    }
  }
}
