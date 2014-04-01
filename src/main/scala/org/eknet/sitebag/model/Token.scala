package org.eknet.sitebag.model

import java.security.SecureRandom
import porter.model.{Ident, Password}
import porter.auth.{Vote, OneSuccessfulVote, AuthResult}

case class Token(token: String) {
  def toSecret = Password(Token.secretName)(token)
}

object Token {
  private val chars = ('a' to 'z') ++ ('A' to 'Z') ++ ('0' to '9')
  private val secretName = Ident("token.0")

  def random: Token = {
    val random = SecureRandom.getInstance("SHA1PRNG")
    val cs = for (i <- 0 to 32) yield chars(random.nextInt(chars.length))
    Token(cs.mkString)
  }

  object Decider extends porter.auth.Decider {
    def apply(result: AuthResult) =
      OneSuccessfulVote(result) && findSuccessVote(result) == Some(secretName)

    private def findSuccessVote(result: AuthResult) =
      result.votes.find { case (k, v) => v == Vote.Success } map (_._1)
  }
}
