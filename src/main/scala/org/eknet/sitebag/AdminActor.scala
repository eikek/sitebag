package org.eknet.sitebag

import java.util.concurrent.TimeUnit
import akka.actor.{Props, ActorRef, ActorLogging, Actor}
import akka.pattern.pipe
import akka.util.Timeout
import porter.model.{Ident, Group, Password, Account}
import porter.client.messages._
import porter.util._
import org.eknet.sitebag.model._
import scala.concurrent.Future
import scala.util.control.NonFatal
import org.eknet.sitebag.mongo.{SitebagMongo, ReextractActor}
import porter.app.client.PorterContext

object AdminActor {
  def apply(reextrRef: ActorRef, porter: PorterContext, mongo: SitebagMongo, settings: SitebagSettings) =
    Props(classOf[AdminActor], reextrRef, porter, mongo, settings)
}
class AdminActor(reextrRef: ActorRef, porter: PorterContext, mongo: SitebagMongo, settings: SitebagSettings) extends Actor with ActorLogging {
  import context.dispatcher

  private implicit val timeout = Timeout(2, TimeUnit.SECONDS)
  private implicit val duration = timeout.duration

  def removeSitebagAccount(account: Ident) =
    if (settings.porterModeIsEmbedded) {
      Future.sequence(porter.deleteAccount(account) :: porter.deleteGroup(account) :: Nil)
        .map(list => OperationFinished(list.map(_.success).reduce(_ && _), None))
    } else {
      porter.updateGroup(account, _.updatedRules(_.filterNot(_.startsWith("sitebag:"))))
    }

  def receive = {
    case CreateUser(account, password) =>
      val group = Group(account, Map.empty, Set(s"sitebag:${account.name}:*"))
      val token = Token.random
      val acc = Account(
        name = account,
        secrets = Password(password) :: token.toSecret :: Nil
      ).updatedProps(UserInfo.token.set(token))

      val f = for {
        cg <- porter.updateGroup(group)
        ca <- porter.createNewAccount(acc.updatedGroups(_ + group.name))
      } yield ca
      f map { makeResult("New user created.") } pipeTo sender

    case DeleteUser(account) =>
      val f = for {
        dtr <- mongo.deleteData(account)
        OperationFinished(dar, error) <- removeSitebagAccount(account)
      } yield
        if (dtr && dar) Success("Account removed.")
        else Failure(s"Failure removing account '${account.name}'. Parts of it may have been removed.", error)

      f.recover({ case ex => Failure(s"Failure removing account '${account.name}'", Some(ex)) }) pipeTo sender

    case GenerateToken(account) =>
      val newtoken = Token.random
      val f = modifyAccount(account) { acc =>
        acc.changeSecret(newtoken.toSecret)
          .updatedProps(UserInfo.token.set(newtoken))
      }
      f map { _ =>
        Success(newtoken.token, "Token generated")
      } recover { case NonFatal(x) =>
        Failure("Token generation failed: "+ x.getLocalizedMessage, Some(x))
      } pipeTo sender

    case ChangePassword(account, password) =>
      val f = modifyAccount(account)(_.changeSecret(Password(password)))
      f map { makeResult("Password changed.") } pipeTo sender

    case req: ReExtractContent =>
      reextrRef forward req
  }
  
  private def modifyAccount(name: Ident)(f: Account => Account) = porter.updateAccount(name, f)

  private def makeResult(msg: String)(of: OperationFinished) = of match {
    case OperationFinished(true, _) => Success(msg)
    case OperationFinished(false, error) => Failure("", error)
    case _ => Failure(s"Unknown error for '$msg'.")
  }
}
