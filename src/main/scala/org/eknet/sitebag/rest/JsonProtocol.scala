package org.eknet.sitebag.rest

import porter.client.json.ModelJsonProtocol
import org.eknet.sitebag._
import org.eknet.sitebag.model._
import spray.json._
import spray.http.{DateTime, Uri}
import spray.routing.authentication.UserPass
import scala.reflect.ClassTag

trait JsonProtocol extends ModelJsonProtocol {
  implicit val userpassCredFormat = jsonFormat2(UserPassCredentials)
  implicit val tokenpassFormat = jsonFormat2(TokenCredentials)

  implicit val nullFormat = new JsonFormat[Null] {
    def read(json: JsValue) = json match {
      case JsNull => null
      case x => deserializationError("Expected JsNull, but got: "+ x)
    }
    def write(obj: Null) = JsNull
  }

  implicit val uriFormat = new JsonFormat[Uri] {
    def read(json: JsValue) = json match {
      case JsString(value) => Uri(value)
      case x => deserializationError("Expected url string as JsString, but got " + x)
    }
    def write(obj: Uri) = JsString(obj.toString())
  }

  implicit val dateTimeFormat = new JsonFormat[DateTime] {
    def read(json: JsValue) = json match {
      case JsString(value) => DateTime.fromIsoDateTimeString(value)
        .getOrElse(deserializationError(s"Cannot parse date: $value"))
      case x => deserializationError("Expected date-time string as JsString, but got " + x)
    }
    def write(obj: DateTime) = JsString(obj.toIsoDateTimeString)
  }

  implicit val tagFormat = new JsonFormat[Tag] {
    def read(json: JsValue) = json match {
      case JsString(value) => Tag(value)
      case x => deserializationError("Cannot create Tag from json: "+ x)
    }

    def write(obj: Tag) = JsString(obj.name)
  }

  class ResultFormat[T](tformat: JsonFormat[Option[T]]) extends RootJsonFormat[Result[T]] {

    def read(json: JsValue) = json match {
      case JsObject(fields) =>
        val success = BooleanJsonFormat.read(fields("success"))
        val message = StringJsonFormat.read(fields("message"))
        if (success) {
          val value = fields.get("value").flatMap(tformat.read)
          Success(value, message)
        } else {
          Failure(message)
        }
      case x => deserializationError("Cannot create result object from: "+ x)
    }

    def write(obj: Result[T]) = {
      obj match {
        case Success(value, msg) =>
          val data = Map("success" -> BooleanJsonFormat.write(true), "message" -> StringJsonFormat.write(msg))
          if (value == None) JsObject(data)
          else JsObject(data + ("value" -> tformat.write(value)))
        case Failure(msg, error) => JsObject(
          "success" -> BooleanJsonFormat.write(false),
          "message" -> StringJsonFormat.write(msg + error.map(e => ": "+ e.getLocalizedMessage).getOrElse(""))
        )
      }
    }
  }
  implicit def resultFormat[T](implicit tf: JsonFormat[Option[T]]) = new ResultFormat[T](tf)

  implicit val tokenFormat = jsonFormat1(Token.apply)

  implicit val newpassFormat = jsonFormat1(NewPassword)

  implicit val raddFormat = jsonFormat3(RAdd)
  implicit val pageEntryFormat = new RootJsonFormat[PageEntry] {
    def read(json: JsValue) = json match {
      case JsObject(fields) =>
        val id = fields("id").convertTo[String]
        val title = fields("title").convertTo[String]
        val content = fields("content").convertTo[String]
        val shortText = fields("shortText").convertTo[String]
        val url = fields("url").convertTo[Uri]
        val archived = fields("archived").convertTo[Boolean]
        val created = fields("created").convertTo[DateTime]
        val tags = fields("tags").convertTo[Set[Tag]]
        val pe = PageEntry(title, url, content, shortText, archived, created, tags.toSet)
        if (pe.id != id) {
          deserializationError("Ids of PageEntry do not match.")
        }
        pe
      case x => deserializationError("Cannot create PageEntry from: "+x)
    }

    def write(obj: PageEntry) = JsObject(
      "id" -> obj.id.toJson,
      "title" -> obj.title.toJson,
      "content" -> obj.content.toJson,
      "shortText" -> obj.shortText.toJson,
      "url" -> obj.url.toJson,
      "urlHost" -> obj.url.authority.host.address.toJson,
      "archived" -> obj.archived.toJson,
      "created" -> obj.created.toJson,
      "createdYear" -> obj.created.year.toJson,
      "createdMonth" -> obj.created.month.toJson,
      "createdDay" -> obj.created.day.toJson,
      "tags" -> obj.tags.toList.toJson
    )
  }

  implicit val pageFormat = jsonFormat(Page.apply, "num", "size")
  implicit val flagFormat = jsonFormat1(Flag)
  implicit val taginputFormat = jsonFormat1(TagInput.apply)
  implicit val tagFilterFormat = jsonFormat1(TagFilter.apply)
  implicit val entrySearchFormat = jsonFormat5(EntrySearch.apply)

  implicit val taglistFormat = jsonFormat2(TagList)
  implicit val deleteActionFormat = jsonFormat1(DeleteAction)
  implicit val reextractActionFormat = jsonFormat1(ReextractAction)
}

object JsonProtocol extends JsonProtocol
