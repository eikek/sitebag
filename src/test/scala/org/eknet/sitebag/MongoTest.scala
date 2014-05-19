package org.eknet.sitebag

import scala.concurrent.duration._
import akka.util.Timeout
import org.eknet.sitebag.mongo.{MongoStoreActor, SitebagMongo}
import akka.actor.{ActorSystem, ActorRef}
import scala.concurrent.Await
import java.util.concurrent.atomic.AtomicReference
import com.typesafe.config.ConfigFactory
import reactivemongo.api.MongoDriver

trait MongoTest extends ActorTestBase {
  import system.dispatcher

  val settings = SitebagSettings(system)
  implicit val timeout: Timeout = 5.seconds

  private var usedDbs: List[String] = Nil
  var dbname = ""
  var mongo: SitebagMongo = _
  var storeRef: ActorRef = _

  before {
    dbname = testName + System.currentTimeMillis()
    usedDbs = dbname :: usedDbs
    mongo = MongoTest.createMongoClient(dbname)
    storeRef = system.actorOf(MongoStoreActor(dbname))
  }

  after {
    mongo.close()
    Thread.sleep(1000)
  }

  override def afterAll() = {
    mongo.close()
    Thread.sleep(1000)
    usedDbs foreach { name => Await.ready(settings.makeMongoClient(name).db.drop(), 10.seconds) }
    super.afterAll()
  }
}

object MongoTest {

  private lazy val config = ConfigFactory.load("application")

  private lazy val mongoClientUrl = config.getString("sitebag.mongodb-url")

  def createMongoClient(dbname: String) = new SitebagMongo(new MongoDriver(), mongoClientUrl, "testdb")
}