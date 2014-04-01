package org.eknet.sitebag

import porter.model.{Credentials, Ident}

package object model {


  /**
   * Messages received from clients.
   */
  trait SitebagMessage

  case class CreateUser(newaccount: Ident, newpassword: String) extends SitebagMessage
  case class GenerateToken(account: Ident) extends SitebagMessage
  case class TokenResult(success: Boolean, message: String, token: String) extends SitebagMessage
  case class ChangePassword(account: Ident, newpassword: String)
}
