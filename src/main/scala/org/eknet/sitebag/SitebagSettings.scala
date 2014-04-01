package org.eknet.sitebag

import akka.actor.{ExtensionIdProvider, ExtensionId, Extension, ExtendedActorSystem}
import porter.model.Ident
import porter.app.akka.{PorterRef, Porter}
import org.eknet.sitebag.model.Token
import com.mongodb.casbah.{MongoClientURI, MongoClient}
import porter.app.client.PorterContext

class SitebagSettings(system: ExtendedActorSystem) extends Extension {
  private val config = system.settings.config.getConfig("sitebag")

  def mongoDbUrl: String = config.getString("mongodb-url")

  def dbName: String = config.getString("dbname")

  def porterMode: String = config.getString("porter.mode")
  def porterRealm = Ident(config.getString("porter.realm"))
  def porterRemoteUrl = config.getString("porter.remote.url")

  private val _porterRef = {
    if (porterMode == "remote") {
      PorterRef(Porter(system).select(porterRemoteUrl))
    }
    else if (porterMode == "embedded") {
      PorterRef(Porter(system).createPorter(system, "sitebag.porter.embedded", "porter"))
    }
    else throw new IllegalArgumentException("Unknown porter mode: "+ porterMode)
  }

  val porter = PorterContext(_porterRef, porterRealm)
  val tokenContext = PorterContext(_porterRef, porterRealm, Token.Decider)

  val mongoClient = MongoClient(MongoClientURI(mongoDbUrl))
}

object SitebagSettings extends ExtensionId[SitebagSettings] with ExtensionIdProvider {
  def createExtension(system: ExtendedActorSystem) = new SitebagSettings(system)
  def lookup() = SitebagSettings
}
