package org.eknet.sitebag

import scala.util.Try

//like a `Try` with an additional message
sealed trait Result[+T] extends Serializable {
  def isSuccess: Boolean
  def isFailure: Boolean
  def message: String
  def map[B](f: Option[T] => Option[B]): Result[B]
  def mapmap[B](f: T => B): Result[B]
  def flatMap[B](f: Option[T] => Result[B]): Result[B]
}

final case class Failure(customMessage: String, error: Option[Throwable] = None) extends Result[Nothing] {
  require(customMessage.nonEmpty || error.isDefined, "Either an error message or an exception must be supplied")
  val isSuccess = false
  val isFailure = true

  def message = if (customMessage.nonEmpty) customMessage else error.map(_.getMessage).getOrElse("An error occured.")
  def map[B](f: (Option[Nothing]) => Option[B]) = this
  def mapmap[B](f: (Nothing) => B) = this

  def flatMap[B](f: (Option[Nothing]) => Result[B]) = this
}
object Failure {
  def apply(error: Throwable): Failure = Failure("", Some(error))
  def apply(message: String): Failure = {
    require(message.nonEmpty, "The error message must not be empty")
    Failure(message, None)
  }
}
final case class Success[T](value: Option[T], message: String) extends Result[T] {
  val isSuccess = true
  val isFailure = false

  def map[B](f: (Option[T]) => Option[B]) = Try(f(value)) match {
    case scala.util.Success(b) => Success(b, message)
    case scala.util.Failure(ex) => Failure(ex.getMessage, Some(ex))
  }
  def flatMap[B](f: (Option[T]) => Result[B]) = Try(f(value)) match {
    case scala.util.Success(b) => b
    case scala.util.Failure(ex) => Failure(ex)
  }

  def mapmap[B](f: (T) => B) = value match {
    case Some(v) => Try(f(v)) match {
      case scala.util.Success(b) => Success(Some(b), message)
      case scala.util.Failure(ex) => Failure(ex.getMessage, Some(ex))
    }
    case None => Success[B](None, message)
  }
}
object Success {
  def apply[T](value: Option[T]): Success[T] = Success(value, "Operation successful.")
  def apply[T](value: T): Success[T] = Success(Some(value), "Operation successful.")
  def apply(msg: String): Ack = Success(None, msg)
  def apply[T](value: T, msg: String): Success[T] = Success(Some(value), msg)
}

