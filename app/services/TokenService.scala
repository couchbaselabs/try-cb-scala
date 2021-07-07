package services

import io.jsonwebtoken.{Jwts, SignatureAlgorithm}
import play.api.Configuration
import play.api.libs.json.Json

import javax.inject.Inject
import scala.util.{Failure, Success, Try}

class TokenService @Inject()(configuration: Configuration) {
  private val secret = configuration.get[String]("jwt.secret")

  def buildJwtToken(username: String) = {
    val json = Json.obj("user" -> username).toString
    Jwts.builder.signWith(SignatureAlgorithm.HS512, secret)
      .setPayload(json)
      .compact
  }

  def verifyAuthenticationHeader(authorization: String, expectedUsername: String): Try[Unit] = {
    val token = authorization.replaceFirst("Bearer ", "")
    val tokenName = Jwts.parser.setSigningKey(secret)
      .parseClaimsJws(token)
      .getBody
      .get("user", classOf[String])

    if (expectedUsername != tokenName) Failure(new IllegalStateException("Token and username don't match"))
    else Success(())
  }


}
