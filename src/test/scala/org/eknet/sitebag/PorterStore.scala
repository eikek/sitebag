package org.eknet.sitebag

import porter.store.SimpleStore
import porter.model.{Group, Password, Account, Realm}
import org.eknet.sitebag.model.Token

object PorterStore extends SimpleStore {
  val realm = Realm("default", "Default Realm")

  val password = "test"
  val token = "test"

  val account = Account(
    name = "superuser",
    groups = Set("superuser"),
    props = Map.empty,
    secrets = Password(password) :: Token(token).toSecret :: Nil
  )
  val group  = Group(
    name = "superuser",
    rules = Set("sitebag:*")
  )

  def realms = List(realm)
  def accounts = List(realm -> account)
  def groups = List(realm -> group)
}
