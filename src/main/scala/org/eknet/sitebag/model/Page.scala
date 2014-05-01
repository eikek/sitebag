package org.eknet.sitebag.model

case class Page(num: Int, size: Option[Int]) {
  require(num > 0, "pages start at 1")
  val isFirst = num == 1
  val maxCount = num * size.getOrElse(24)
  val startCount = (num -1) * size.getOrElse(24)
  def next = Page(num +1, size)
  def prev = Page(num -1, size)
}