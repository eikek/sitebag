package org.eknet.sitebag

import java.util.concurrent.TimeUnit
import akka.actor.{Props, ActorRef, ActorLogging, Actor}
import akka.pattern.pipe
import akka.util.Timeout
import porter.model.{Ident, Group, Password, Account, Realm}
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

  override def preStart() {
    if (settings.createAdminAccount) {
      log.info("Create admin account ...")
      val f = createAdminAccount
      f onSuccess { case result ⇒ log.info(result.toString) }
      f onFailure { case failed ⇒ log.error(failed, "Error creating admin account") }
    }
  }

  def removeSitebagAccount(account: Ident) =
    Future.sequence(porter.deleteAccount(account) :: porter.deleteGroup(account) :: Nil)
      .map(list => OperationFinished(list.map(_.success).reduce(_ && _), None))

  def receive = {
    case CreateUser(account, password) =>
      val group = Group(account, Map.empty, Set(s"sitebag:${account.name}:*"))
      val token = Token.random
      val acc = Account(
        name = account,
        secrets = Password(password) :: token.toSecret :: Nil
      ).updatedProps(UserInfo.token.set(token))

      def makeAccount: Account => Account = _.updatedGroups(_ + group.name).updatedProps { props =>
        if (UserInfo.token.get(props).isDefined) props
        else UserInfo.token.set(token).apply(props)
      }
      val f = for {
        cg <- porter.updateGroup(account, _.updatedRules(_ + s"sitebag:${account.name}:*")).recoverWith({ case _ => porter.updateGroup(group) })
        ca <- porter.updateAccount(account, makeAccount).recoverWith({ case _ => porter.createNewAccount(acc.updatedGroups(_ + group.name)) })
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

    case req: ReExtractStatusRequest ⇒
      reextrRef forward req
  }
  
  private def modifyAccount(name: Ident)(f: Account => Account) = porter.updateAccount(name, f)

  private def makeResult(msg: String)(of: OperationFinished) = of match {
    case OperationFinished(true, _) => Success(msg)
    case OperationFinished(false, error) => Failure("", error)
    case _ => Failure(s"Unknown error for '$msg'.")
  }

  private def createAdminAccount: Future[Result[String]] = {
    val realm = Realm(settings.porterRealm, "Default Realm")
    val group = Group("admin", Map.empty, Set(s"sitebag:*"))
    val token = Token.random
    val acc = Account(
      name = "admin",
      groups = Set(group.name),
      secrets = Password("admin") :: token.toSecret :: Nil
    ).updatedProps(UserInfo.token.set(token))

    val f = for {
      rr ← porter.updateRealm(realm)
      cg ← porter.updateGroup(group.name, _.updatedRules(_ + s"sitebag:${acc.name.name}:*")).recoverWith({ case _ => porter.updateGroup(group) })
      ra ← porter.createNewAccount(acc)
    } yield ra
    f map { makeResult("Admin user created.") } 
  }
}
