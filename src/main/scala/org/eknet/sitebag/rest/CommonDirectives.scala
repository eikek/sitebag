package org.eknet.sitebag.rest

import spray.routing._
import spray.httpx.unmarshalling._
import spray.httpx.marshalling.ToResponseMarshaller
import spray.json.RootJsonFormat
import spray.httpx.SprayJsonSupport
import java.util.Locale
import spray.http.HttpHeaders
import porter.model.PropertyList._
import porter.model.Account
import scala.Some
import scala.util.Try

trait CommonDirectives extends Directives with FormUnmarshaller {

  def unmarshalFormOrJson[A](json: RootJsonFormat[A], fdm: FormDataUnmarshaller[A]) =
    Unmarshaller.oneOf(SprayJsonSupport.sprayJsonUnmarshaller(json), fdm)

  implicit def unm[A, B](implicit json: RootJsonFormat[A], fdm: FormDataUnmarshaller[A]) = UnmarshallerLifting.fromRequestUnmarshaller(
    UnmarshallerLifting.fromMessageUnmarshaller(unmarshalFormOrJson(json, fdm)))

  def handle[A, B](f: A => B)(implicit json: RootJsonFormat[A], fdm: FormDataUnmarshaller[A], m: ToResponseMarshaller[B]): Route = {
    handleWith(f)
  }

  def requestLocale: Directive1[Locale] = headerValuePF {
    case HttpHeaders.`Accept-Language`(lang) if lang.nonEmpty =>
      Locale.forLanguageTag(lang.head.toString())
  }

  def accountLocale(account: Account): Directive1[Locale] = {
    val loc = for {
      accloc <- locale.get(account.props)
      loc <- Try(Locale.forLanguageTag(accloc)).toOption
    } yield loc
    loc.map(provide).getOrElse(reject)
  }

  def localeWithDefault(acc: Option[Account] = None): Directive1[Locale] =
    acc match {
      case Some(a) => accountLocale(a) | requestLocale | provide(Locale.getDefault)
      case _ => requestLocale | provide(Locale.getDefault)
    }

  def checkboxActive(name: String): Directive0 =
    formField(name.?).flatMap(x => if (x.exists(_ equalsIgnoreCase "on")) pass else reject())

  def param(name: String): Directive1[String] = parameter(name).recover(_ => reject())

  def fromParams[A](ds: FieldsDeserialize[A]): Directive1[A] = parameterSeq.map(ds.fromFields)
}
object CommonDirectives extends CommonDirectives