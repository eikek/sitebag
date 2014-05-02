package org.eknet.sitebag

import porter.store.{MutableStore, SimpleStore}
import porter.model._
import org.eknet.sitebag.model.Token
import porter.model.Group
import porter.model.Realm
import porter.model.Account
import scala.concurrent.{Future, ExecutionContext}

object PorterStore extends SimpleStore with MutableStore {
  val realm = Realm("default", "Default Realm")

  val password = "test"
  val token = "test"

  val superuser = Account(
    name = "superuser",
    groups = Set("superuser"),
    props = PropertyList.mutableSource.toTrue(Map.empty),
    secrets = Password(password) :: Token(token).toSecret :: Nil
  )
  val superuser2 = Account(
    name = "superuser2",
    groups = Set("superuser"),
    props = PropertyList.mutableSource.toTrue(Map.empty),
    secrets = Password(password) :: Token("abc").toSecret :: Nil
  )
  val mary = Account(
    name = "mary",
    groups = Set("mary"),
    secrets = Password(password) :: Token("abc").toSecret :: Nil
  )

  val superuserGroup  = Group(
    name = "superuser",
    props = PropertyList.mutableSource.toTrue(Map.empty),
    rules = Set("sitebag:*")
  )
  val maryGroup = Group(
    name = "mary",
    rules = Set("sitebag:mary:*")
  )

  def realms = List(realm)
  def accounts = List(realm -> superuser, realm -> superuser2, realm -> mary)
  def groups = List(realm -> superuserGroup, realm -> maryGroup)


  override def findAccounts(realm: Ident, names: Set[Ident])(implicit ec: ExecutionContext) =
    super.findAccounts(realm, names).map(accs => accs.map(_.updatedProps(PropertyList.mutableSource.toTrue)))

  def updateRealm(realm: Realm)(implicit ec: ExecutionContext) = Future.successful(true)
  def updateAccount(realm: Ident, account: Account)(implicit ec: ExecutionContext) = Future.successful(true)
  def deleteGroup(realm: Ident, groupId: Ident)(implicit ec: ExecutionContext) = Future.successful(true)
  def updateGroup(realm: Ident, group: Group)(implicit ec: ExecutionContext) = Future.successful(true)
  def deleteAccount(realm: Ident, accId: Ident)(implicit ec: ExecutionContext) = Future.successful(true)
  def deleteRealm(realm: Ident)(implicit ec: ExecutionContext) = Future.successful(true)
}
