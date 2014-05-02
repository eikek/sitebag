package org.eknet.sitebag.mongo

import java.io.ByteArrayOutputStream
import scala.concurrent.{Promise, ExecutionContext, Future}
import scala.util.control.NonFatal
import akka.util.ByteString
import play.api.libs.iteratee.Iteratee
import spray.http.{ContentType, Uri, DateTime}
import reactivemongo.core.commands.LastError
import reactivemongo.api._
import reactivemongo.bson._
import reactivemongo.api.gridfs._
import reactivemongo.api.gridfs.Implicits._
import reactivemongo.core.iteratees.CustomEnumerator.SEnumerator
import reactivemongo.api.collections.default.BSONCollection
import porter.model.Ident
import org.eknet.sitebag._
import org.eknet.sitebag.model._
import org.eknet.sitebag.content.Content
import org.eknet.sitebag.utils._

class SitebagMongo(driver: MongoDriver, url: String, dbName: String)(implicit ec: ExecutionContext) {

  val mongoUri = MongoConnection.parseURI(url).get

  val connection = driver.connection(mongoUri)
  val db = connection(dbName)
  val gridFs = new GridFS(db)
  def files: BSONCollection = gridFs.files

  def entries(account: Ident): BSONCollection = db(s"${account.name}_entries")
  def tags(account: Ident): BSONCollection = db(s"${account.name}_tags")

  type BD = BSONDocument
  val BD = BSONDocument


  object DuplicateKey {
    def unapply(e: LastError): Option[LastError] =
      if (e.code == Some(11000)) Some(e) else None
  }

  private val errorLogger: PartialFunction[Throwable, Any] = {
    case e => new Exception("Error completing mongo-db future.", e).printStackTrace()
  }

  // ~~~~~~
  import SitebagMongo._

  private def updateArchivedFlag(account: Ident, entryId: String, ts: DateTime)(f: PageEntryMetadata => Boolean) = {
    for {
      meta <- entries(account).find(Id(entryId), PageEntryMetadata.filter).one[PageEntryMetadata]
      result <- meta.map { pe =>
        if (pe.updated <= ts) {
          entries(account).update(Id(entryId), BD("$set" -> BD("archived" -> f(pe)))).makeResult("Archived status changed.")
        } else {
          //ignore outdated update
          Future.successful(Success("Archived status unchanged."))
        }
      }.getOrElse(Future.successful(Success(None)))
    } yield result
  }

  def setArchivedFlag(account: Ident, entryId: String, ts: DateTime, flag: Boolean) = {
    updateArchivedFlag(account, entryId, ts)(_ => flag)
  }

  def toggleArchivedFlag(account: Ident, entryId: String, ts: DateTime) = {
    updateArchivedFlag(account, entryId, ts)(pe => !pe.archived)
  }

  def countBinaries(): Future[Int] = {
    files.find(BD()).cursor[BD].enumerate().run(Iteratee.fold(0) { (i, _) => i+1})
  }

  def addBinary(bin: Binary): Future[ReadFile[BSONValue]] = {
    val exists: Future[Option[ReadFile[BSONValue]]] = gridFs.find(Id(bin.id)).headOption
    exists.flatMap {
      case Some(b) =>
        files.update(Id(bin.id), BD("$addToSet" -> BD("urls" -> bin.url))).map(_ => b)

      case _ =>
        val meta = BinaryMetaData(bin.id, Set(bin.url), bin.md5, bin.contentType, bin.created)
        val md = DefaultFileToSave(
          bin.url.filename,
          Some(bin.contentType.value),
          Some(System.currentTimeMillis()),
          BD(),
          BSONString(bin.id))

        val push = new SEnumerator(bin.data.toArray)(_ => None, _ => ())
        val f = for {
          file <- gridFs.save(push, md)
          _   <- files.update(Id(bin.id), BD("$set" -> meta))
        } yield file
        f.onFailure(errorLogger)
        f
    }
  }

  def getBinary[S](selector: S)(implicit sWriter: BSONDocumentWriter[S]): Future[Option[Binary]] = {
    def readData(f: ReadFile[BSONValue]): Future[ByteString] = {
      val buffer = new ByteArrayOutputStream()
      gridFs.readToOutputStream(f, buffer).map(_ => ByteString(buffer.toByteArray))
    }
    def makeBinary(f: ReadFile[BSONValue], data: ByteString): Binary = {
      //todo convert existing data and use f.metadata document
      val meta = f.asInstanceOf[DefaultReadFile].original.as[BinaryMetaData]
      val ct = f.contentType.flatMap(parseContentType).orElse(contentTypeByExtension(meta.uris.head))
      Binary(meta.id, meta.uris.head, data, meta.md5, ct.getOrElse(Binary.octetstream), meta.created)
    }
    for {
      file <- gridFs.find(selector).headOption
      bin  <- file.map(f => readData(f).map(bs => Some(makeBinary(f, bs))))
                  .getOrElse(Future.successful(None))
    } yield bin
  }

  def addEntryBinary(account: Ident, entryId: String, bin: Binary) = {
    (for {
      file   <- addBinary(bin)
      result <- {
        val f1 = files.update(Id(bin.id), BD("$inc" -> BD("used" -> 1)))
        val f2 = entries(account).update(Id(entryId), BD("$addToSet" -> BD("binary-ids" -> bin.id)))
        Future.sequence(f1 :: f2 :: Nil)
      }
    } yield result).makeResult("Binary saved.")
  }

  def getEntryBinary(account: Ident, entryId: String): Future[Option[Binary]] = {
    for {
      entry <- entries(account).find(Id(entryId)).one[PageEntryMetadata]
      cid   <- entry.flatMap(_.contentId).map(id => getBinary(Id(id)))
                    .getOrElse(Future.successful(None))
    } yield cid
  }

  def getEntryContent(account: Ident, entryId: String): Future[Result[Content]] = {
    getEntryBinary(account, entryId).map(optb => optb.map(b =>
      Success(Content(b.url,b.data, Some(b.contentType)))).getOrElse(Success(None, s"Entry '$entryId' not found."))
    )
  }

  def getEntry(account: Ident, entryId: String): Future[Result[PageEntry]] = {
    val page = entries(account).find(Id(entryId)).one[PageEntry]
    val tag  = getTags(account, Set(entryId))
    for {
      p <- page
      t <- tag
    } yield Success(p.map(e => e.copy(tags = t.get(entryId).getOrElse(Set.empty))))
  }

  def getEntryMeta(account: Ident, entryId: String): Future[Result[PageEntryMeta]] = {
    val page = entries(account).find(Id(entryId)).one[PageEntryMetadata]
    val tag  = getTags(account, Set(entryId))
    for {
      p <- page
      t <- tag
    } yield Success(p.map(md => PageEntryMeta(md.uri, md.archived, md.created, t.get(entryId).getOrElse(Set.empty))))
  }

  def addEntry(account: Ident, entry: FullPageEntry): Future[Ack] = {
    val addcontentId: PartialFunction[Ack, Unit] = {
      case Success(_, _) =>
        val org = Binary(entry.page)
        addEntryBinary(account, entry.entry.id, org).onSuccess {
          case Success(_, _) =>
            entries(account).update(Id(entry.entry.id), BD("$set" -> BD("content-id" -> org.id)))
        }
    }
    val f = entries(account).insert(entry.entry).recover({
      case DuplicateKey(x) => successLastError("Page already present.")
    }).makeResult("Page added.")
    f.onSuccess(addcontentId)
    f
  }

  def cleanUnusedBinaries: Future[LastError] = {
    val delete = Iteratee.fold1[BD, LastError](Future.successful(successLastError)) { (prev, doc) =>
      doc.getAs[String]("_id") match {
        case Some(i) => gridFs.remove(BSONString(i))
        case _       => Future.successful(successLastError)
      }
    }

    files.find(BD("used" -> 0), BD("_id" -> 1))
      .cursor[BD]
      .enumerate().run(delete)
  }

  def withPageEntries(account: Ident)(cons: PageEntryMetadata => Unit) = {
    entries(account).find(BD("_id" -> BD("$exists" -> true)))
      .cursor[PageEntryMetadata]
      .enumerate().run(Iteratee.fold(0){ (c, x) => cons(x); c+1 })
  }

  def deleteEntry(account: Ident, entryId: String): Future[Ack] = {
    val promise = Promise[Ack]()
    val bins = entries(account).find(Id(entryId)).one[PageEntryMetadata].map(_.map(_.binaryIds).getOrElse(Set.empty))
    bins onFailure { case ex => promise.failure(ex) }
    bins onSuccess { case binIds =>
      val removed = entries(account).remove(Id(entryId))
      removed onFailure { case ex => promise.failure(ex) }
      removed onSuccess {
        case ack if !ack.ok => promise.failure(new Exception(s"Remove of page '$entryId' failed"))
        case _ =>
          val f1 = files.update(BD("_id" -> BD("$in" -> binIds)), BD("$inc" -> BD("used" -> -1)), multi = true).flatMap {
            result => if (result.ok) cleanUnusedBinaries else Future.failed(new Exception("Decremting binary counter failed"))
          }
          val f2 = clearEntryTags(account, entryId)
          val fall = Future.sequence(f1 :: f2 :: Nil).makeResult("Page removed.")
          fall onComplete promise.complete
      }
    }
    promise.future
  }

  def cleanTags(account: Ident): Future[LastError] = {
    tags(account).remove(BD("entries" -> BD("$size" -> 0)))
  }

  def clearEntryTags(account: Ident, entryId: String): Future[LastError] = {
    tags(account).update(BD("entries" -> entryId), BD("$pull" -> BD("entries" -> entryId)), multi = true).flatMap { error =>
      cleanTags(account)
    }
  }

  def setTags(account: Ident, entryId: String, tagnames: Set[Tag]) = {
    clearEntryTags(account, entryId).flatMap { _ =>
      tagEntries(account, entryId, tagnames)
    }
  }

  def tagEntries(account: Ident, entryId: String, tagnames: Set[Tag]): Future[Ack] = {
    Future.sequence(for (tn <- tagnames) yield {
      tags(account).update(Id(tn.name),
        BD("$addToSet" -> BD("entries" -> entryId)),
        upsert = true
      )
    }).makeResult("Page tagged.")
  }

  def untagEntries(account: Ident, entryId: String, tagnames: Set[Tag]): Future[Ack] = {
    val f = tags(account).update(
      BD("_id" -> BD("$in" -> tagnames.map(_.name).toList)),
      BD("$pull" -> BD("entries" -> entryId)),
      multi = true
    )
    f.flatMap(_ => cleanTags(account)).makeResult("Page untagged.")
  }

  def listTags(account: Ident, regex: String): Future[Result[TagList]] = {
    val ts = tags(account).find(BD("_id" -> BD("$exists" -> true))).sort(BD("_id" -> 1)).cursor[TagRecord].collect[List]()
    ts map { set =>
      val list = set.distinct.withFilter(_.name matches regex)
         .map(r => Tag(r.name) -> r.entries.size)
         .toList
      Success(TagList(list.map(_._1), list.toMap))
    }
  }

  def getEntryTags(account: Ident, entryId: String): Future[Result[List[Tag]]] = {
    val f = getTags(account, Set(entryId)).map(_.get(entryId).getOrElse(Set.empty).toList)
    f.map(l => Success(l)).recover({
      case NonFatal(e) => Failure(e)
    })
  }

  def getTags(account: Ident, entryIds: Set[String]): Future[Map[String, Set[Tag]]] = {
    tags(account).find(BD("_id" -> BD("$exists" -> true))).cursor[TagRecord].collect[Set]().map { trs =>
      val ts = for {
        tr <- trs
        id <- entryIds
        if tr.entries contains id
      } yield id -> tr.name
      ts.toSet.groupBy((s: (String, String)) => s._1) map {
        case (k, l) => k -> l.map(t => Tag(t._2))
      }
    }
  }


  def listEntries(account: Ident, tagnames: Set[Tag], archived: Option[Boolean], page: Page, complete: Boolean): Future[Result[List[PageEntry]]] = {
    val selector = tags(account).find(BD("_id" -> BD("$in" -> tagnames.map(_.name))))
      .cursor[TagRecord]
      .collect[Set]()
      .map { set =>
        val ids = if (set.isEmpty) Set.empty[String] else set.map(_.entries).reduce(_ & _)
        if (ids.isEmpty && tagnames.nonEmpty) (ids, BD("_id" -> false))
        else (ids, makeFilter(ids, archived))
      }

    def loadPages[A](sel: BD, filter: BD = BD.empty)(implicit reader: BSONDocumentReader[A]) =
      entries(account).find(sel, filter).sort(BD("created" -> -1))
        .cursor[A]
        .enumerate(maxDocs = page.maxCount)
        .run(Iteratee.fold((0, Vector.empty[A])) { case ((index, list), p) =>
        if (index >= page.startCount && index < page.maxCount) (index +1, list :+ p)
        else (index +1, list)
      }).map(_._2.toList)

    selector.flatMap {
      case (ids, sel) if complete =>
        val pages = loadPages[PageEntry](sel)
        for {
          p <- pages
          t <- getTags(account, p.map(_.id).toSet)
        } yield Success(p.map { e => e.copy(tags = t.get(e.id).getOrElse(Set.empty)) })

      case (ids, sel) =>
        val pages = loadPages[PageEntryMetadata](sel)
        for {
          p <- pages
          t <- getTags(account, p.map(_.id).toSet)
        } yield Success(p.map { e => PageEntry(e.title, e.uri, "", e.shortText, e.archived, e.created,t.get(e.id).getOrElse(Set.empty)) })
    }
  }

  def makeFilter(ids: Set[String], archived: Option[Boolean]): BD = {
    val idF: Option[BD] = if (ids.nonEmpty) Some(BD("_id" -> BD("$in" -> ids))) else None
    val archivedF: Option[BD] = archived map (r => BD("archived" -> r))

    idF.toList ::: archivedF.toList match {
      case Nil => BD("_id" -> BD("$exists" -> true))
      case a :: Nil => a
      case more => BD("$and" -> more)
    }
  }

  def findBinaryById(id: String): Future[Result[Binary]] = {
    getBinary(Id(id)).map(b => Success(b)).recover({
      case NonFatal(e) => Failure(e)
    })
  }

  def findBinaryByUrl(url: String): Future[Result[Binary]] = {
    getBinary(BD("urls" -> url)).map(b => Success(b)).recover({
      case NonFatal(e) => Failure(e)
    })
  }

  def updateEntryContent(account: Ident, entryId: String, title: String, content: String, shortText: String): Future[Ack] = {
    entries(account)
      .update(Id(entryId), BD("$set" -> BD("title" -> title, "content" -> content, "shortText" -> shortText)))
      .makeResult("Entry content updated.")
  }
}
object SitebagMongo {
  import language.implicitConversions

  type BR[T] = BSONDocumentReader[T]
  type BW[T] = BSONDocumentWriter[T]
  type BH[B <: BSONValue, T] = BSONHandler[B, T]

  val successLastError = LastError(ok = true, None, None, None, None, 0, updatedExisting = false)
  def successLastError(msg: String) = LastError(ok = true, None, None, Some(msg), None, 0, updatedExisting = false)

  case class Id(id: String)

  implicit val idWriter: BW[Id] = new BW[Id] {
    def write(t: Id) = BSONDocument("_id" -> BSONString(t.id))
  }

  implicit val dateTimeHandler: BH[BSONString, DateTime] = new BH[BSONString, DateTime] {
    def read(bson: BSONString) =
      DateTime.fromIsoDateTimeString(bson.value)
        .getOrElse(sys.error(s"Unable to parse DateTime '${bson.value}."))
    def write(t: DateTime) = BSONString(t.toIsoDateTimeString)
  }

  implicit val uriHandler: BH[BSONString, Uri] = new BH[BSONString, Uri] {
    def read(bson: BSONString) = Uri(bson.value)
    def write(t: Uri) = BSONString(t.toString())
  }

  implicit val contentTypeHandler: BH [BSONString, ContentType] = new BH[BSONString, ContentType] {
    def read(bson: BSONString) = parseContentType(bson.value)
      .getOrElse(sys.error("Unable to parse content type from: "+ bson.value))
    def write(t: ContentType) = BSONString(t.value)
  }

  case class PageEntryMetadata(id: String, uri: Uri, title: String, shortText: String, archived: Boolean, created: DateTime, updated: DateTime, contentId: Option[String], binaryIds: Set[String])
  object PageEntryMetadata {
    val filter = BSONDocument("_id" -> 1, "url" -> 1, "title" -> 1, "shortText" -> 1,
      "archived" -> 1, "created" -> 1, "updated" -> 1, "binary-ids" -> 1, "content-id" -> 1)
  }
  implicit val pageEntryMetaReader: BR[PageEntryMetadata] = new BR[PageEntryMetadata] {
    def read(bson: BSONDocument) = PageEntryMetadata(
      bson.getAs[String]("_id").get,
      bson.getAs[Uri]("url").getOrElse(sys.error("No uri Property")),
      bson.getAs[String]("title").getOrElse(sys.error("No title property")),
      bson.getAs[String]("shortText").getOrElse(sys.error("No shortText property")),
      bson.getAs[Boolean]("archived").getOrElse(false),
      bson.getAs[DateTime]("created").getOrElse(sys.error("No created property")),
      bson.getAs[DateTime]("updated").getOrElse(DateTime(0L)),
      bson.getAs[String]("content-id"),
      bson.getAs[Set[String]]("binary-ids").getOrElse(Set.empty)
    )
  }

  case class BinaryMetaData(id: String, uris: Set[Uri], md5: String, contentType: ContentType, created: DateTime, used: Int = 0)
  implicit val binaryMetaDataReader: BR[BinaryMetaData] = new BR[BinaryMetaData] {
    def read(bson: BSONDocument) = BinaryMetaData(
      bson.getAs[String]("_id").getOrElse(sys.error("No _id property")),
      bson.getAs[Set[Uri]]("urls").getOrElse(sys.error("No url property")),
      bson.getAs[String]("md5").getOrElse(sys.error("No md5 property")),
      bson.getAs[ContentType]("contentType").getOrElse(sys.error("No contentType property")),
      bson.getAs[DateTime]("created").getOrElse(sys.error("No created property")),
      bson.getAs[Int]("used").getOrElse(0)
    )
  }
  implicit val binaryMetaDataWriter: BW[BinaryMetaData] = new BW[BinaryMetaData] {
    def write(t: BinaryMetaData) = BSONDocument(
      "urls" -> t.uris,
      "md5" -> t.md5,
      "contentType" -> t.contentType,
      "created" -> t.created,
      "used" -> t.used
    )
  }

  implicit val pageEntryReader: BSONDocumentReader[PageEntry] = new BSONDocumentReader[PageEntry] {
    def read(bson: BSONDocument) = {
      PageEntry(
        bson.getAs[String]("title").get,
        bson.getAs[Uri]("url").get,
        bson.getAs[String]("content").get,
        bson.getAs[String]("shortText").getOrElse("..."),
        bson.getAs[Boolean]("archived").get,
        bson.getAs[DateTime]("created").get
      )
    }
  }

  implicit val pageEntryWriter: BSONDocumentWriter[PageEntry] = new BSONDocumentWriter[PageEntry] {
    def write(obj: PageEntry) = BSONDocument(
      "_id" -> obj.id,
      "title" -> obj.title,
      "url" -> obj.url,
      "content" -> obj.content,
      "shortText" -> obj.shortText,
      "archived" -> obj.archived,
      "created" -> obj.created,
      "updated" -> DateTime.now
    )
  }

  case class TagRecord(name: String, entries: Set[String])
  implicit val tagRecordReader: BR[TagRecord] = new BR[TagRecord] {
    def read(bson: BSONDocument) = TagRecord(
      bson.getAs[String]("_id").getOrElse(sys.error("Tag record has no _id attribute")),
      bson.getAs[Set[String]]("entries").getOrElse(Set.empty)
    )
  }
  implicit val tagRecordWriter: BW[TagRecord] = new BW[TagRecord] {
    def write(t: TagRecord) = BSONDocument(
      "_id" -> t.name,
      "entries" -> t.entries
    )
  }


  def errorToResult(successMsg: String)(error: LastError): Ack = {
    if (error.ok) {
      Success(error.errMsg.getOrElse(successMsg))
    } else {
      Failure(
        error.errMsg.getOrElse("An error occured.") +
          error.err.map(" Code: "+_).getOrElse("") +
          error.err.map(" Error: "+_).getOrElse("")
      )
    }
  }
  implicit class ResultConvertError(f: Future[LastError])(implicit ec: ExecutionContext) {
    def makeResult(successMsg: String): Future[Ack] = f.map(errorToResult(successMsg))
  }
  implicit class ResultConvertErrorOption(f: Future[Option[LastError]])(implicit ec: ExecutionContext) {
    def makeResult(successMsg: String): Future[Ack] = f map {
      case Some(error) => errorToResult(successMsg)(error)
      case None => Success(None, successMsg)
    }
  }
  implicit class ResultConvertErrorList(f: Future[Traversable[LastError]])(implicit ec: ExecutionContext) {
    def makeResult(successMsg: String): Future[Ack] = f.map { errors =>
      errors.collect({ case r if !r.ok => r }).headOption match {
        case Some(error) => errorToResult(successMsg)(error)
        case _ => Success(successMsg)
      }
    }
  }
}