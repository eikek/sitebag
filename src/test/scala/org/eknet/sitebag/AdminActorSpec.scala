package org.eknet.sitebag

import scala.concurrent.duration._
import porter.model.{Group, Account, Ident}
import scala.concurrent.Await
import org.eknet.sitebag.model.Page

class AdminActorSpec extends ActorTestBase("AdminActor") with MongoTest {
  import system.dispatcher
  val porter = settings.porter
  val remoteSettings = new SitebagSettings {
    def tokenContext = settings.tokenContext
    def config = settings.config
    def mongoDriver = settings.mongoDriver
    def porter = settings.porter
    def logger(c: Class[_]) = settings.logger(c)
    def extractor = settings.extractor
    override val porterMode = "remote"
  }
  val embeddedRef = system.actorOf(AdminActor(null, porter, mongo, settings))

  val remoteRef = system.actorOf(AdminActor(null, porter, mongo, remoteSettings))


  def findEntries = Await.result(mongo.listEntries("mary", Set.empty, None, Page.one, false).map(_.toOption.get), timeout.duration)

  "AdminActor" should {
    "remove accounts if porter is embedded" in {
      Await.ready(porter.createNewAccount(Account("mary")), timeout.duration)
      Await.ready(mongo.addEntry("mary", commons.newEntry), timeout.duration)

      awaitCond(findEntries.nonEmpty, 10.seconds)
      embeddedRef ! DeleteUser("mary")
      expectMsg(Success("Account removed."))

      awaitCond(findEntries.isEmpty, 15.seconds)

      assert(Await.result(porter.findAccounts(Set("mary")), timeout.duration).accounts === Set.empty)
      assert(Await.result(porter.findGroups(Set("mary")), timeout.duration).groups === Set.empty)
    }

    "remove permissions if porter is remote" in {
      val mary = Account("mary", Map.empty, Set("mary"))
      Await.ready(porter.createNewAccount(mary), timeout.duration)
      val maryg = Group("mary", Map.empty, Set("sitebag:mary:*"))
      Await.ready(porter.updateGroup(maryg), timeout.duration)
      Await.ready(mongo.addEntry("mary", commons.newEntry), timeout.duration)

      awaitCond(findEntries.nonEmpty, 10.seconds)
      remoteRef ! DeleteUser("mary")
      expectMsg(Success("Account removed."))

      awaitCond(findEntries.isEmpty, 15.seconds)

      assert(Await.result(porter.findAccounts(Set("mary")), timeout.duration).accounts === Set(mary))
      assert(Await.result(porter.findGroups(Set("mary")), timeout.duration).groups === Set(maryg.copy(rules = Set.empty)))
    }
  }
}
