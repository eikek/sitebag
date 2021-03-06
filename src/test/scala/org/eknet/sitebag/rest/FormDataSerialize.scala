package org.eknet.sitebag.rest

import org.eknet.sitebag.CreateUser
import spray.http.FormData

trait FormDataSerialize {

  implicit class CreateUserSerialize(cu: CreateUser) {
    def toFormData = FormData(Map("newaccount" → cu.newaccount.name, "newpassword" → cu.newpassword))
  }

  implicit class NewPasswordSerialize(np: NewPassword) {
    def toFormData = FormData(Map("newpassword" → np.newpassword))
  }

  implicit class FlagSerialze(f: Flag) {
    def toFormData = FormData(Map("flag" -> f.flag.toString))
  }

  implicit class DeleteSeriaize(da: DeleteAction) {
    def toFormData = FormData(Map("delete" -> da.delete.toString))
  }

  implicit class FormDataAdds(fd: FormData) {
    def as(username: String, password: String) = FormData(fd.fields ++ Seq("username" → username, "password" → password))
  }
}
