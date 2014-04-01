package org.eknet.sitebag.model

case class Tag(name: String) {
  require(Tag.isValidTagname(name), s"tag name is not valid: $name")
}

object Tag {
  import scala.language.implicitConversions

  private val chars = ('a' to 'z') ++ ('A' to 'Z') ++ ('0' to '9') ++ Seq('-', '_')

  implicit def fromString(s: String) = Tag(s)

  val favourite = Tag("favourite")

  def isValidTagname(name: String): Boolean = {
    @scala.annotation.tailrec def loop(i: Int): Boolean =
      if (i >= name.length) i > 0
      else
      if (chars contains name.charAt(i)) loop(i+1)
      else false

    loop(0)
  }
}
