package org.eknet.sitebag.model

import scala.util.Try

final class Tag private(val name: String) extends Serializable {
  override def equals(o: Any): Boolean = o match {
    case other: Tag => other.name == name
    case _ => false
  }
  override def hashCode: Int = name.hashCode
  override def toString = s"Tag($name)"
}

object Tag {
  import scala.language.implicitConversions

  lazy val favourite = fromString("favourite")

  private val chars = ('a' to 'z') ++ ('0' to '9') ++ Seq('-', '_')

  implicit def fromString(s: String): Tag = {
    val name = s.toLowerCase
    require(Tag.isValidTagname(name), s"tag name is not valid: $name")
    new Tag(name)
  }
  def apply(s: String): Tag = fromString(s)

  def unapply(t: Tag): Option[String] = Some(t.name)

  def tryApply(s: String) = Try(fromString(s)).toOption

  def convertString(s: String): Tag = Tag(s.filter(chars.contains))

  def isValidTagname(name: String): Boolean = {
    @scala.annotation.tailrec def loop(i: Int): Boolean =
      if (i >= name.length) i > 0
      else if (chars contains name.charAt(i)) loop(i+1)
      else false

    loop(0)
  }
}
