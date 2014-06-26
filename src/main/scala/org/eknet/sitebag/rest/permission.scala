package org.eknet.sitebag.rest

import porter.model.Ident

object permission {

  val createUser = Set("sitebag:createuser")

  def deleteUser(username: Ident) = Set(s"sitebag:${username.name}:delete")

  def listTags(username: Ident) = Set(s"sitebag:${username.name}:listtags")

  def generateToken(username: Ident) = Set(s"sitebag:${username.name}:generatetoken")

  def changePassword(username: Ident) = Set(s"sitebag:${username.name}:changepassword")

  def getEntry(username: Ident, id: String) = Set(s"sitebag:${username.name}:entry:get:$id")

  def getEntries(username: Ident) = Set(s"sitebag:${username.name}:entry:get")

  def addEntry(username: Ident) = Set(s"sitebag:${username.name}:entry:add")

  def deleteEntry(username: Ident, id: String) = Set(s"sitebag:${username.name}:entry:delete:$id")

  def updateEntry(username: Ident, id: String) = Set(s"sitebag:${username.name}:entry:update:$id")

}









