package org.eknet.sitebag.content

import java.nio.charset.Charset

case class HtmlMeta(charset: Option[Charset], language: Option[String], title: String)
