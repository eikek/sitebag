package org.eknet.sitebag.rest

import spray.httpx.unmarshalling._
import spray.http.{HttpEntity, Uri, FormData, ContentTypeRange}
import spray.http.MediaTypes._
import org.eknet.sitebag.model.{Page, Tag}
import spray.json.DeserializationException
import org.eknet.sitebag.TagList
import scala.util.Try

trait FormUnmarshaller {

  implicit val newpasswordFormData = FormDataUnmarshaller[NewPassword] { fields =>
    NewPassword(fields.toMap.apply("newpassword"))
  }

  implicit val raddFormData = FormDataUnmarshaller[RAdd] { fields =>
    val data = fields.toMap
    RAdd(Uri(data("url")), data.get("title"), TagInput.fromFields(fields).tags)
  }

  implicit val flagFormdata = FormDataUnmarshaller[Flag] { fields =>
    Flag(fields.toMap.get("flag").exists(_.toBoolean))
  }

  implicit val deleteActionFormData = FormDataUnmarshaller[DeleteAction] { fields =>
    val fm = fields.toMap
    DeleteAction(fm.get("delete").get != null)
  }

  implicit val tagInputFormData = FormDataUnmarshaller[TagInput](TagInput.fromFields)

  implicit val reextractActionFormData = FormDataUnmarshaller[ReextractAction] { fields =>
    ReextractAction(fields.toMap.get("entryId"))
  }
}

final class FormDataUnmarshaller[A] private (um: Unmarshaller[A]) extends Unmarshaller[A] {
  def apply(e: HttpEntity) = um(e)
}
object FormDataUnmarshaller {
  def apply[A](f: Fields => A): FormDataUnmarshaller[A] = new FormDataUnmarshaller (
    Unmarshaller.delegate(ContentTypeRange(`application/x-www-form-urlencoded`)) {
      fd: FormData => f(fd.fields)
    }
  )
}

object FormUnmarshaller extends FormUnmarshaller
