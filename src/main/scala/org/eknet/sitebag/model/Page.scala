package org.eknet.sitebag.model

case class Page(num: Int, size: Option[Int]) {
  require(num > 0, "pages start at 1")
  val isFirst = num == 1
  val pageSize = size getOrElse 24
  val maxCount = num * pageSize
  val startCount = (num -1) * pageSize
  def next = Page(num +1, size)
  def prev = Page(num -1, size)
}

object Page {

  val one = Page(1)
  val two = one.next
  val three = two.next

  def apply(num: Int): Page = Page(num, None)
}