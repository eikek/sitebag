package org.eknet.sitebag

import akka.actor._
import porter.model.Ident
import porter.app.akka.{PorterRef, Porter}
import org.eknet.sitebag.model.{PageEntry, Token}
import org.eknet.sitebag.content.Extractor
import scala.reflect.ClassTag
import porter.app.client.PorterContext
import com.typesafe.config.Config
import scala.util.Try
import porter.util.{AES, Base64}
import porter.auth.{SomeSuccessfulVote, AuthResult, Decider}
import spray.http.Uri
import org.eknet.sitebag.rest.EntrySearch
import org.eknet.sitebag.mongo.SitebagMongo
import reactivemongo.api.MongoDriver
import akka.event.Logging
import scala.concurrent.ExecutionContext

class SitebagSettings(system: ExtendedActorSystem) extends Extension {
  private val config = system.settings.config.getConfig("sitebag")

  def makeSubconfig[T](name: String, f: Config => T): T = f(config.getConfig(name))

  val mongoDbUrl: String = config.getString("mongodb-url")

  val dbName: String = config.getString("dbname")

  system.log.info(s"Using mongo database '$dbName' at '$mongoDbUrl'")

  val porterMode: String = config.getString("porter.mode")
  val porterModeIsEmbedded = porterMode == "embedded"
  val porterRealm = Ident(config.getString("porter.realm"))
  val porterRemoteUrl = config.getString("porter.remote.url")

  val extractor = makeExtractor

  private def makeExtractor: Extractor = {
    import collection.JavaConverters._
    val maker = makeInstance[Extractor](system.dynamicAccess)_ andThen (_.get)
    Extractor.combine(config.getConfigList("extractors").asScala.map(c => maker(c)) :+ Extractor.errorFallback)
  }

  private lazy val _porterRef = {
    if (porterMode == "remote") {
      PorterRef(Porter(system).select(porterRemoteUrl))
    }
    else if (porterModeIsEmbedded) {
      PorterRef(Porter(system).fromSubConfig(system, "sitebag.porter.embedded", "porter"))
    }
    else throw new IllegalArgumentException("Unknown porter mode: "+ porterMode)
  }

  lazy val porter = PorterContext(_porterRef, porterRealm, new Decider {
    def apply(result: AuthResult) =
      SomeSuccessfulVote(result) && Decider.existsSuccessVote(result, _ != Token.secretName)
  })
  val tokenContext = PorterContext(_porterRef, porterRealm, Token.Decider)
  val cookieKey = Try(config.getString("cookie-key")).map(Base64.decode).getOrElse(AES.generateRandomKey).toVector

  private lazy val mongoDriver = new MongoDriver(system)
  def makeMongoClient(dbname: String)(implicit ec: ExecutionContext) =
    new SitebagMongo(mongoDriver, mongoDbUrl, dbname)
  lazy val defaultMongoClient = makeMongoClient(dbName)(system.dispatcher)

  val bindPort = config.getInt("bind-port")
  val bindHost = config.getString("bind-host")

  val baseUrl = Uri(config.getString("url-base"))
  val pathPrefix = baseUrl.path.toString()

  def apiUri(suffix: String): Uri = Uri(suffix).resolvedAgainst(Uri("api/").resolvedAgainst(baseUrl))
  def apiUri(user: String, suffix: String): Uri = apiUri(user +"/"+ suffix)
  def uiUri(suffix: String) = Uri(suffix).resolvedAgainst(Uri("ui/").resolvedAgainst(baseUrl))
  def entryUiUri(entry: PageEntry) = uiUri(s"entry/${entry.id}")
  def binaryUri(url: String) = Uri("bin").resolvedAgainst(baseUrl).withQuery("url" -> url.toString)
  def binaryIdUri(id: String) = Uri("bin/"+id).resolvedAgainst(baseUrl)
  def wbUrl(user: String) = Uri(s"wb/$user").resolvedAgainst(baseUrl)
  def rssFeedUrl(user: String, token: Token, q: EntrySearch): Uri = {
    val u1 = apiUri(user, s"entries/rss/${token.token}")
    val qmap =  Map(
      "tags" -> q.tag.tags.map(_.name).mkString(","),
      "archived"-> q.archived.map(_.toString).getOrElse(""),
      "complete" -> q.complete.toString
    ).filter(_._2.nonEmpty)
    u1.withQuery(qmap)
  }

  val telnetHost = config.getString("porter.embedded.telnet.host")
  val telnetPort = config.getInt("porter.embedded.telnet.port")
  val telnetEnabled = config.getBoolean("porter.embedded.telnet.enabled")

  val webuiEnabled = config.getBoolean("enable-web-ui")
  val logRequests = config.getBoolean("log-requests")
  val trustAllSsl = config.getBoolean("trust-all-ssl")

  def logger(c: Class[_]) = Logging(system, c)

  private def makeInstance[T: ClassTag](dynAccess: DynamicAccess)(cfg: Config) = {
    val className = cfg.getString("class")
    val params = cfg.getConfig("params")
    createInstanceOf(dynAccess, params)(className)
  }

  private def createInstanceOf[T: ClassTag](dynAccess: DynamicAccess, cfg: Config)(fqcn: String): Try[T] = {
    lazy val loadObject = dynAccess.getObjectFor(fqcn)
    val args = scala.collection.immutable.Seq.empty[(Class[_], AnyRef)]
    lazy val configCtor = dynAccess.createInstanceFor(fqcn, args :+ (classOf[Config] -> cfg))
    val defctor = dynAccess.createInstanceFor(fqcn, args)

    configCtor orElse defctor orElse loadObject match {
      case r @ scala.util.Success(_) => r
      case scala.util.Failure(ex) => scala.util.Failure(new Exception(s"Unable to create instance: $fqcn", ex))
    }
  }
}

object SitebagSettings extends ExtensionId[SitebagSettings] with ExtensionIdProvider {
  def createExtension(system: ExtendedActorSystem) = new SitebagSettings(system)
  def lookup() = SitebagSettings
}
