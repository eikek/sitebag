package org.eknet.sitebag

import scala.concurrent.duration._
import porter.model._
import scala.concurrent.Await
import org.eknet.sitebag.model.{UserInfo, Page}
import porter.model.Group
import porter.model.Account
import porter.client.messages.OperationFinished

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
  }
  val embeddedRef = system.actorOf(AdminActor(null, porter, mongo, settings))

  val remoteRef = system.actorOf(AdminActor(null, porter, mongo, remoteSettings))

  def findEntries = Await.result(mongo.listEntries("mary", Set.empty, None, Page.one, false).map(_.toOption.get), timeout.duration)

  "AdminActor" should {
    "remove accounts if porter is embedded" in {
      Await.ready(mongo.withDbName(settings.dbName).db.drop(), 10.seconds)
      assert(Await.result(porter.createNewAccount(Account("mary")), timeout.duration) === OperationFinished(true, None))
      Await.ready(mongo.addEntry("mary", commons.newEntry), timeout.duration)

      awaitCond(findEntries.nonEmpty, 10.seconds)
      embeddedRef ! DeleteUser("mary")
      expectMsg(Success("Account removed."))

      awaitCond(findEntries.isEmpty, 15.seconds)

      assert(Await.result(porter.findAccounts(Set("mary")), timeout.duration).accounts === Set.empty)
      assert(Await.result(porter.findGroups(Set("mary")), timeout.duration).groups === Set.empty)
    }

    "remove permissions if porter is remote" in {
      Await.ready(mongo.withDbName(settings.dbName).db.drop(), 10.seconds)
      val mary = Account("mary", Map.empty, Set("mary"))
      assert(Await.result(porter.createNewAccount(mary), timeout.duration).success === true)
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

    "update user if it exists" in {
      Await.ready(mongo.withDbName(settings.dbName).db.drop(), 10.seconds)
      val sally = Account("sally", PropertyList.email.set("sally@mail.com")(Map.empty), Set("admins"), Password("sally") :: Nil)
      assert(Await.result(porter.createNewAccount(sally), timeout.duration).success === true)

      embeddedRef ! CreateUser(sally.name, "sally")
      expectMsg(Success("New user created."))

      val acc = Await.result(porter.findAccounts(Set(sally.name)), timeout.duration).accounts.head
      assert(acc.groups === Set(Ident("sally"), Ident("admins")))
      assert(acc.props(PropertyList.email.name) === "sally@mail.com")
      assert(acc.props(UserInfo.token.name).nonEmpty)
      val group = Await.result(porter.findGroups(Set(sally.name)), timeout.duration).groups.head
      assert(group.rules === Set("sitebag:sally:*"))
    }

    "create new user if not existent" in {
      Await.ready(mongo.withDbName(settings.dbName).db.drop(), 10.seconds)
      embeddedRef ! CreateUser("sally", "sally")
      expectMsg(Success("New user created."))

      val acc = Await.result(porter.findAccounts(Set("sally")), timeout.duration).accounts.head
      assert(acc.groups === Set(Ident("sally")))
      assert(acc.props(UserInfo.token.name).nonEmpty)
      val group = Await.result(porter.findGroups(Set("sally")), timeout.duration).groups.head
      assert(group.rules === Set("sitebag:sally:*"))
    }
  }
}
