package org.eknet.sitebag.rest

import spray.httpx.unmarshalling._
import spray.http.{Uri, FormData, ContentTypeRange}
import spray.http.MediaTypes._
import org.eknet.sitebag.model.{Page, Tag}
import spray.json.DeserializationException
import org.eknet.sitebag.TagList
import scala.util.Try

trait FormUnmarshaller {

  def formDataDelegate[A](f: Fields => A): Unmarshaller[A] =
    Unmarshaller.delegate(ContentTypeRange(`application/x-www-form-urlencoded`)) {
      fd: FormData => f(fd.fields)
    }

  implicit val newpasswordFormData = formDataDelegate[NewPassword] { fields =>
    NewPassword(fields.toMap.apply("newpassword"))
  }

  implicit val raddFormData = formDataDelegate[RAdd] { fields =>
    val data = fields.toMap
    RAdd(Uri(data("url")), data.get("title"), TagInput.fromFields(fields).tags)
  }

  implicit val flagFormdata = formDataDelegate[Flag] { fields =>
    Flag(fields.toMap.get("flag").exists(_.toBoolean))
  }

  implicit val deleteActionFormData = formDataDelegate[DeleteAction] { fields =>
    val fm = fields.toMap
    DeleteAction(fm.get("delete").get != null)
  }
}

object FormUnmarshaller extends FormUnmarshaller
