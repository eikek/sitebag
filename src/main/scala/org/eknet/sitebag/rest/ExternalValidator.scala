package org.eknet.sitebag.rest

import java.net.URLEncoder
import java.util.UUID

import akka.actor.{ActorRefFactory, ActorSystem}
import akka.util.Timeout
import org.eknet.sitebag.SitebagSettings
import org.eknet.sitebag.model.{Token, UserInfo}
import porter.auth.{Vote, AuthToken, Validator}
import porter.client.messages.OperationFinished
import spray.http.Uri
import spray.http._
import spray.client.pipelining._

import scala.concurrent.{Future, ExecutionContext, Await}
import scala.util.{Failure, Success, Try}


class ExternalValidator(system: ActorSystem) extends Validator {
  import porter.model._

  //must be lazy because its inside SitebagSettings(system)
  lazy val settings = SitebagSettings(system)
  val log = akka.event.Logging(system, this.getClass)

  implicit val refFactory: ActorRefFactory = system
  implicit val executionContext: ExecutionContext = system.dispatcher


  def authenticate(token: AuthToken) = {
    (for {
      (usePost, urlPattern) <- settings.externalAuthentication
    } yield {
      val userpass = token.credentials collect { case c: PasswordCredentials => c }
      userpass.foldLeft(token) { (token, up) =>
        val replaced = urlPattern.replace("%[username]", up.accountName.name)
          .replace("%[password]", up.password)
        if (verifyPassword(usePost, replaced, up.accountName.name)) token.vote(Ident("external") -> Vote.Success)
        else token.vote(Ident("external") -> Vote.Failed())
      }
    }).getOrElse(token)
  }

  private def verifyPassword(usePost: Boolean, url: String, username: String): Boolean = {
    import org.eknet.sitebag.utils._
    import scala.concurrent.duration._
    implicit val timeout: Timeout = 15.seconds

    val client: SendReceive = sendReceive
    val uri = Uri.sanitized(url)

    val authf = if (usePost) {
      client(Post(uri, queryContent(url)))
    } else {
      client(Get(uri))
    }
    authf.onComplete {
      case Success(resp) => log.debug("External Auth response: "+ resp.toString)
      case Failure(ex) => log.error(ex, "Error in external authentication.")
    }

    val authbool = authf.map { resp =>
      if (resp.status.isSuccess) {
        //create account
        val token = Token.random
        val acc = Account(
          name = username,
          groups = Set(username),
          secrets = Password(UUID.randomUUID().toString) :: token.toSecret :: Nil
        ).updatedProps(UserInfo.token.set(token))
        val group = Group(name = username, rules = Set(s"sitebag:$username:*"))

        val fc = settings.porter.findAccounts(Set(username)).flatMap { found =>
          if (found.accounts.isEmpty) {
            for {
              cg ← settings.porter.updateGroup(group.name, _.updatedRules(_ + s"sitebag:$username:*"))
                .recoverWith({ case _ => settings.porter.updateGroup(group) })
              ra ← settings.porter.createNewAccount(acc)
            } yield ra
          } else {
            Future.successful(OperationFinished.success)
          }
        }
        Await.result(fc, 15.seconds).success
      } else {
        resp.status.isSuccess
      }
    }

    Await.result(authbool, 20.seconds)
  }

  private def queryContent(url: String) = {
    url.indexOf('?') match {
      case i if i > 0 =>
        val query = url.substring(i + 1).split('&').map(_.split('=').toList match {
          case key :: value :: Nil => key :: URLEncoder.encode(value, "utf-8") :: Nil
          case list => list
        })
        query.map(_.mkString("=")).mkString("&")
      case _ => ""
    }
  }
}
