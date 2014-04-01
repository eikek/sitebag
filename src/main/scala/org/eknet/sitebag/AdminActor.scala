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

object AdminActor {
  def apply(storeRef: ActorRef) = Props(classOf[AdminActor], storeRef)
}
class AdminActor(storeRef: ActorRef) extends Actor with ActorLogging {
  import context.dispatcher

  private val settings = SitebagSettings(context.system)
  private implicit val timeout = Timeout(2, TimeUnit.SECONDS)
  private implicit val duration = timeout.duration

  def receive = {
    case CreateUser(account, password) =>
      val group = Group(account, Map.empty, Set(s"sitebag:${account.name}:*"))
      val acc = Account(name = account, secrets = Password(password) :: Nil)

      val f = for {
        cg <- settings.porter.updateGroup(group)
        ca <- settings.porter.createNewAccount(acc.updatedGroups(_ + group.name))
      } yield ca
      f map { makeResult("New user created.") } pipeTo sender

    case GenerateToken(account) =>
      val newtoken = Token.random
      val f = modifyAccount(account)(_.changeSecret(newtoken.toSecret)) map { _ =>
        TokenResult(success = true, "Token generated", newtoken.token)
      }
      f.recover {
        case NonFatal(x) => TokenResult(success = false, "Token generation failed: "+ x.getLocalizedMessage, "")
      } pipeTo sender

    case ChangePassword(account, password) =>
      val f = modifyAccount(account)(_.changeSecret(Password(password)))
      f map { makeResult("Password changed.") } pipeTo sender
  }
  
  private def modifyAccount(name: Ident)(f: Account => Account) =
    for {
      resp <- settings.porter.findAccounts(Set(name))
      acc <- Future.immediate(resp.accounts.headOption.getOrElse(sys.error(s"No account '$name'")))
      nacc <- settings.porter.updateAccount(f(acc))
    } yield nacc

  private def makeResult(msg: String)(of: OperationFinished) = of match {
    case OperationFinished(true, _) => Result.success(msg)
    case OperationFinished(false, Some(error)) => Result.failed(error)
    case _ => Result.failed(s"Unknown error for '$msg'.")
  }
}
