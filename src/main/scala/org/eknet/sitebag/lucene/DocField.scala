package org.eknet.sitebag.lucene

import scala.reflect.ClassTag
import org.apache.lucene.document._
import org.apache.lucene.document.FieldType.NumericType

case class DocField[T](name: String, value: T, index: Boolean = true, tokenize: Boolean = true, store: Boolean = false) {

  def indexed = if (index) this else copy(index = true)
  def stored = if (store) this else copy(store = true)
  def tokenized = if (tokenize) this else copy(tokenize = true)

  def notIndexed = if (index) copy(index = false) else this
  def notStored = if (store) copy(store = false) else this
  def notTokenized = if (tokenize) copy(tokenize = false) else this

}
object DocField {
  import language.implicitConversions

  private implicit class FieldTypeOps(ft: FieldType) {
    def freezed: FieldType = {
      ft.freeze()
      ft
    }
  }
  implicit def toLuceneField[T](df: DocField[T]): Field = {
    val ft = new FieldType()
    ft.setIndexed(df.index)
    ft.setStored(df.store)
    ft.setTokenized(df.tokenize)
    df.value match {
      case s: String => new Field(df.name, s, ft.freezed)
      case i: Int    =>
        ft.setNumericType(NumericType.INT)
        new IntField(df.name, i, ft.freezed)
      case l: Long   =>
        ft.setNumericType(NumericType.LONG)
        new LongField(df.name, l, ft.freezed)
      case f: Float =>
        ft.setNumericType(NumericType.FLOAT)
        new FloatField(df.name, f, ft.freezed)
      case d: Double =>
        ft.setNumericType(NumericType.DOUBLE)
        new DoubleField(df.name, d, ft.freezed)
      case _ =>
        new Field(df.name, df.value.toString, ft.freezed)
    }
  }
}