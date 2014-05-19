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

  val dbname = testName + System.currentTimeMillis()
  val mongo: SitebagMongo = SitebagMongo(settings).withDbName(dbname)
  val storeRef: ActorRef = system.actorOf(MongoStoreActor(mongo))

  before {
    Await.ready(mongo.db.drop(), 10.seconds)
  }

  override def afterAll() = {
    Await.ready(mongo.db.drop(), 10.seconds)
    mongo.close()
    super.afterAll()
  }
}

object MongoTest {

  private lazy val config = ConfigFactory.load("application")

  private lazy val mongoClientUrl = config.getString("sitebag.mongodb-url")

  def createMongoClient(dbname: String) = new SitebagMongo(new MongoDriver(), mongoClientUrl, "testdb")
}