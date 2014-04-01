package org.eknet.sitebag.model

case class Result(success: Boolean, message: String)

object Result {

  def success(msg: String): Result = Result(success = true, msg)
  def failed(msg: String): Result = Result(success = false, msg)
  def failed(e: Throwable): Result = failed(e.getLocalizedMessage)

}
