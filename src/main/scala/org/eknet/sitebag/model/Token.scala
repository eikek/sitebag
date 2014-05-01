package org.eknet.sitebag.model

import java.security.SecureRandom
import porter.model.{Ident, Password}
import porter.auth.{SomeSuccessfulVote, Vote, OneSuccessfulVote, AuthResult}

case class Token(token: String) {
  def toSecret = Password(Token.secretName)(token)
}

object Token {
  private val chars = ('a' to 'z') ++ ('A' to 'Z') ++ ('0' to '9')
  val secretName = Ident("password.token.0")

  def random: Token = {
    val random = SecureRandom.getInstance("SHA1PRNG")
    val cs = for (i <- 0 to 32) yield chars(random.nextInt(chars.length))
    Token(cs.mkString)
  }

  object Decider extends porter.auth.Decider {
    import porter.auth.Decider._
    def apply(result: AuthResult) =
      SomeSuccessfulVote(result) && existsSuccessVote(result, _ == secretName)
  }
}
