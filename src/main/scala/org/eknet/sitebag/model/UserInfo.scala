package org.eknet.sitebag.model

import porter.model.Property.StringProperty
import porter.model.Properties

case class UserInfo(name: String, settings: Map[String, String], token: Option[Token], canCreateUser: Boolean = false) {
  lazy val isEmpty = this == UserInfo.empty
  lazy val nonEmpty = !isEmpty
}

object UserInfo {
  val defaultThemeUrl = "//netdna.bootstrapcdn.com/bootswatch/latest/readable/bootstrap.min.css"
  val themeUrl = StringProperty("sitebag-user-theme-url")
  val token = TokenProperty

  val empty = UserInfo("anonymous", Map.empty, None, false)

  object TokenProperty extends porter.model.Property[Token] {
    def name = "sitebag-user-token"

    override def set(value: Token): Properties => Properties = setRaw(value.token)
    def get(map: Properties) = getRaw(map).map(Token.apply)
  }
}
