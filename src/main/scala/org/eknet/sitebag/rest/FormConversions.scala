package org.eknet.sitebag.rest

import spray.httpx.unmarshalling._
import spray.http.{FormData, ContentTypeRange}
import spray.http.MediaTypes._
import org.eknet.sitebag.model.CreateUser
import porter.model.Ident

trait FormConversions {

  type FormUnmarshaller[A] = Unmarshaller[A]

  private def formDataDelegate[A](f: Map[String, String] => A): FormUnmarshaller[A] =
    Unmarshaller.delegate(ContentTypeRange(`application/x-www-form-urlencoded`)) {
      fd: FormData => f(fd.fields.toMap)
    }

  implicit val createUserFormData = formDataDelegate[CreateUser] { fields =>
    CreateUser(Ident(fields("newaccount")), fields("newpassword"))
  }

  implicit val newpasswordFormData = formDataDelegate[NewPassword] { fields =>
    NewPassword(fields("password"))
  }

}

object FormConversions extends FormConversions
