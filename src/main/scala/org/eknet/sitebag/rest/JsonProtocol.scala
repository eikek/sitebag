package org.eknet.sitebag.rest

import porter.client.json.ModelJsonProtocol
import org.eknet.sitebag.model._

trait JsonProtocol extends ModelJsonProtocol {

  implicit val createUserFormat = jsonFormat2(CreateUser)
  implicit val resultForm = jsonFormat2(Result.apply)

  implicit val tokenFormat = jsonFormat1(Token.apply)
  implicit val tokenResultFormat = jsonFormat3(TokenResult)

  implicit val newpassFormat = jsonFormat1(NewPassword)

}

object JsonProtocol extends JsonProtocol
