package org.eknet.sitebag.rest

import org.eknet.sitebag.model.CreateUser
import spray.http.FormData

trait FormDataSerialize {

  implicit class CreateUserSerialize(cu: CreateUser) {
    def toFormData = FormData(Map("newaccount" -> cu.newaccount.name, "newpassword" -> cu.newpassword))
  }

  implicit class NewPasswordSerialize(np: NewPassword) {
    def toFormData = FormData(Map("password" -> np.password))
  }
}
