package services

import com.couchbase.client.scala.codec.JsonSerializer
import com.couchbase.client.scala.codec.JsonSerializer.PlayEncode
import components.CouchbaseConnection
import play.api.libs.json._
import reactor.core.scala.publisher.{SFlux, SMono}

import java.util.UUID
import javax.inject.Inject
import scala.collection.mutable.ArrayBuffer
import scala.util.{Failure, Success, Try}

case class AuthToken(token: String)

case class AuthResult(context: Seq[String], data: AuthToken)

class AuthenticationNotFoundException extends RuntimeException {
  override def getMessage: String = "Bad username or password"
}

case class BookFlightResult(context: Seq[String], data: JsArray)

object AuthToken {
  implicit val playCodec: OWrites[AuthToken] = Json.writes[AuthToken]
}

object AuthResult {
  implicit val playCodec: OWrites[AuthResult] = Json.writes[AuthResult]
}

object BookFlightResult {
  implicit val playCodec: OWrites[BookFlightResult] = Json.writes[BookFlightResult]
}

class TenantService @Inject()(val couchbase: CouchbaseConnection,
                              val tokenService: TokenService) {
  def signup(tenant: String, username: String, password: String): Try[AuthResult] = {
    import com.github.t3hnar.bcrypt._

    val passHash: Try[String] = password.bcryptSafeBounded

    passHash.flatMap(ph => {
      val json = Json.obj("type" -> "user",
        "name" -> username,
        "password" -> ph)

      val collection = couchbase.bucket.scope(tenant).collection("users")

      collection.insert(username, json)(JsonSerializer.PlayEncode)
        .map(_ => {
          val token = tokenService.buildJwtToken(username)
          AuthResult(Seq(s"KV insert - scoped to ${tenant}.users: document ${username}"), AuthToken(token))
        })
    })
  }

  def login(tenant: String, username: String, password: String): Try[AuthResult] = {
    import com.github.t3hnar.bcrypt._

    val collection = couchbase.bucket.scope(tenant).collection("users")

    collection.get(username) match {
      case Success(doc) =>
        val json = doc.contentAs[JsValue].get.asInstanceOf[JsObject]
        val storedPasswordHash: String = json("password").as[String]

        if (password.isBcryptedSafeBounded(storedPasswordHash).get) {
          val token = tokenService.buildJwtToken(username)
          Success(AuthResult(Seq(s"KV get - scoped to ${tenant}.users: for password field in document ${username}"), AuthToken(token)))
        }
        else {
          Failure(new AuthenticationNotFoundException())
        }
      case Failure(err) => Failure(err)
    }
  }

  def bookFlight(tenant: String, username: String, newFlights: JsArray): Try[BookFlightResult] = {
    val usersCollection = couchbase.bucket.scope(tenant).collection("users")
    val bookingsCollection = couchbase.bucket.scope(tenant).collection("bookings")

    usersCollection.get(username)
      .map(user => {
        val userData = user.contentAs[JsValue].asInstanceOf[JsObject]

        val bookedFlights = userData("flights").as[JsArray]
        val existingBookedFlightIds: Seq[String] = bookedFlights.value.map(v => v.as[String])
        val newlyBookedFlightIds = ArrayBuffer.empty[String]
        val added = ArrayBuffer.empty[JsObject]

        bookedFlights.value.foreach(flightRaw => {
          val flight = flightRaw.as[JsObject]
          val newFlight = flight ++ Json.obj("bookedon" -> "try-cb-scala")
          val flightId = UUID.randomUUID.toString
          bookingsCollection.insert(flightId, newFlight).get
          newlyBookedFlightIds += flightId
          added += newFlight
        })

        val allBookedFlightIds = existingBookedFlightIds ++ newlyBookedFlightIds

        userData ++ Json.obj("flights" -> allBookedFlightIds)
        usersCollection.upsert(username, userData).get

        BookFlightResult(Seq(s"KV update - scoped to ${tenant}.user: for bookings field in document %s"), JsArray(added))
      })
  }

  def getFlights(tenant: String, username: String): Try[BookFlightResult] = {
    val usersCollection = couchbase.bucket.scope(tenant).collection("users")
    val bookingsCollection = couchbase.bucket.scope(tenant).collection("bookings").reactive

    usersCollection.get(username)
      .flatMap(user => {
        val userData = user.contentAs[JsValue].asInstanceOf[JsObject]

        val existingFlights = userData("flights").as[JsArray]

        val x: SMono[Seq[JsObject]] = SFlux.fromIterable(existingFlights.value)
          .flatMap(flight => {
            val flightId = flight.as[String]
            bookingsCollection.get(flightId)
              .map(r => r.contentAs[JsValue].asInstanceOf[JsObject])
          })
          .collectSeq()

        // Usually we would try and return the reactive chain to the user so we are asynchronously streaming results back,
        // but Play's reactive support is currently experimental, so instead just block synchronously.
        val flights: Try[JsArray] = Try(x.block())
          .map(flights => JsArray(flights))

        flights.map(f => BookFlightResult(Seq(s"KV get - scoped to ${tenant}.user: for ${f.value.length} bookings in document ${username}"), f))
      })
  }
}
