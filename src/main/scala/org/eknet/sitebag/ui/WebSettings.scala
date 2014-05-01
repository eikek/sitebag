package org.eknet.sitebag.ui

import com.typesafe.config.Config
import porter.model.Ident
import org.eknet.sitebag.SitebagSettings
import org.eknet.sitebag.utils._

class WebSettings(val base: SitebagSettings, config: Config) {
  val brandName = config.getString("brandname")
  val bootswatchApi = config.getString("bootswatch-api-url")

  val applicationName = org.eknet.sitebag.BuildInfo.name
  val applicationRevision = org.eknet.sitebag.BuildInfo.revision
  val applicationVersion = org.eknet.sitebag.BuildInfo.version
  val applicationBuildTime = org.eknet.sitebag.BuildInfo.buildTime

  val enableChangePassword = config.getBoolean("enable-change-password")

  val bookmarkletRaw = {
    val baseHost = base.baseUrl.authority.toString().substring(2)
    io.Source.fromURL(getClass.getResource("bookmarklet.min.js"))
      .getLines().mkString(";")
      .replace("{{host}}", baseHost)
  }

  require(bookmarkletRaw.nonEmpty, "Bookmarklet file is empty")

  def makeBookmarklet(user: Ident) = {
    val apiUri = base.apiUri(user.name, "entry").toString()
    val validChars =
    ("ABCDEFGHIJKLMNOPQRSTUVWXYZ" +
      "abcdefghijklmnopqrstuvwxyz" +
      "0123456789" +
      "-._~:/").toSet.map((c: Char) => c.toByte)

    bookmarkletRaw.replace("{{userApiUrl}}", apiUri).percentageEncoded(validChars)
  }

  val enableHighlightJs = config.getBoolean("enable-highlightjs")
  val highlightJsTheme = config.getString("highlightjs-theme")
}
