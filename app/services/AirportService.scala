package services

import com.couchbase.client.scala.implicits.Codec
import com.couchbase.client.scala.query.{QueryOptions, QueryParameters}
import components.CouchbaseConnection
import play.api.libs.json.{Json, OWrites}

import javax.inject.Inject
import scala.util.{Success, Try}

case class Airport(airportname: String)
case class AirportsResult(context: Seq[String], data: Seq[Airport])

object Airport {
  // This line allows an `Airport` to be read and written directly from/to Couchbase
  implicit val codec: Codec[Airport] = Codec.codec[Airport]

  // This line allows Play to serialize and deserialize an `Airport` directly from/to JSON
  implicit val playCodec: OWrites[Airport] = Json.writes[Airport]
}

object AirportsResult {
  // This line allows Play to serialize and deserialize an `AirportsResult` directly from/to JSON
  implicit val playCodec: OWrites[AirportsResult] = Json.writes[AirportsResult]
}

class AirportService @Inject()(val couchbase: CouchbaseConnection) {

  /** Fetches all airports for a given `search` string, from the `travel-sample`.inventory.airport collection.
   *
   * This demonstrates the use of Couchbase's query language N1QL.
   */
  def airports(search: String): Try[AirportsResult] = {
    val sameCase = search.equals(search.toUpperCase) || search.equals(search.toLowerCase)

    val (whereClause, params) = if ((search.length == 3) && sameCase) ("faa = $val", search.toUpperCase)
    else if ((search.length == 4) && sameCase) ("icao = $val", search.toUpperCase)
    else ("POSITION(LOWER(airportname), $val) = 0", search.toLowerCase)

    val statement =
      s"""SELECT airportname
                    FROM `${couchbase.bucket.name}`.inventory.airport
                    WHERE $whereClause"""

    val result: Try[AirportsResult] = for {
      // Named parameters are used to avoid any injection issues
      result <- couchbase.cluster.query(statement, QueryOptions().parameters(QueryParameters.Named("val" -> params)))

      // Note how we retrieve the results directly into our `Airport` case class
      rows <- result.rowsAs[Airport]

      ret <- Success(AirportsResult(Seq("N1QL query - scoped to inventory: ", statement), rows))
    } yield ret

    result
  }
}

