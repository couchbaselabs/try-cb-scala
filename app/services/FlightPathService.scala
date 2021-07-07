package services

import com.couchbase.client.scala.implicits.Codec
import com.couchbase.client.scala.json.JsonObject
import com.couchbase.client.scala.query.{QueryOptions, QueryParameters, QueryResult}
import components.CouchbaseConnection
import play.api.Logger
import play.api.libs.json.{Json, OWrites, Writes}

import java.util.Calendar
import java.util.concurrent.ThreadLocalRandom
import javax.inject.Inject
import scala.util.{Failure, Success, Try}


case class FlightPath(destinationairport: String,
                      equipment: String,
                      flight: String,
                      name: String,
                      sourceairport: String,
                      utc: String)

object FlightPath {
  implicit val codec: Codec[FlightPath] = Codec.codec[FlightPath]

  // This line allows Play to serialize and deserialize an `AirportsResult` directly from/to JSON
  // Normally we would just write `implicit val playCodec: OWrites[FlightPath] = Json.writes[FlightPath]`, but we need
  // to add additional fields to the final JSON, so we write a custom serializer.
  implicit val playCodec: Writes[FlightPath] = Writes[FlightPath](fp => {
    val flightTime = ThreadLocalRandom.current().nextInt(8000)
    val price = Math.ceil(flightTime / 8 * 100) / 100

    Json.obj("destinationairport" -> fp.destinationairport,
      "equipment" -> fp.equipment,
      "flight"-> fp.flight,
      "name" -> fp.name,
      "sourceairport" -> fp.sourceairport,
      "utc" -> fp.utc,
      "flighttime" -> flightTime,
      "price" -> price)

  })
}

case class FlightPathsResult(context: Seq[String], data: Seq[FlightPath])

object FlightPathsResult {
  implicit val playCodec: OWrites[FlightPathsResult] = Json.writes[FlightPathsResult]

}

class FlightPathService @Inject()(val couchbase: CouchbaseConnection) {
  private val logger: Logger = Logger(this.getClass.getSimpleName)

  case class AirlineCodes(fromCode: String, toCode: String, dayOfWeek: Int)

  /** Get all flight paths going from airport `from` to airport `to` on date `leave`.
   *
   * We use two N1QL queries for this.  The first looks up the airport codes for `from` and `to`, the second finds the
   * flight paths from the `travel-sample`.inventory.route and `travel-sample`.inventory.airline collections.
   */
  def flightPaths(from: String, to: String, leave: Calendar): Try[FlightPathsResult] = {
    val statement =
      s"""SELECT faa as fromAirport
    FROM `${couchbase.bucket.name}`.inventory.airport
    WHERE airportname = $$from
    UNION
    SELECT faa as toAirport
    FROM `${couchbase.bucket.name}`.inventory.airport
    WHERE airportname = $$to"""

    val paramsTry: Try[AirlineCodes] = couchbase.cluster.query(statement, QueryOptions().parameters(QueryParameters.Named("from" -> from, "to" -> to)))
      .flatMap(result => FlightPathService.findFromQuery(result, "fromAirport", from)
        .flatMap(fromAirport => FlightPathService.findFromQuery(result, "toAirport", to)
          .map(toAirport => AirlineCodes(fromAirport, toAirport, leave.get(Calendar.DAY_OF_WEEK)))))

    paramsTry match {
      case Success(p) =>
        findFlightPaths(p).map(r => {
          val newContext: Seq[String] = Seq("N1QL query - scoped to inventory: ", statement) :+ r._1
          FlightPathsResult(newContext, r._2)
        })

      case Failure(err) =>
        val msg = s"Problems finding details for airports ${from} ${to}: ${err}"
        logger.warn(msg)
        Failure(new RuntimeException(msg))
    }
  }

  private def findFlightPaths(p: AirlineCodes): Try[(String, Seq[FlightPath])] = {
    val statement =
      s"""SELECT a.name, s.flight, s.utc, r.sourceairport, r.destinationairport, r.equipment
              FROM `${couchbase.bucket.name}`.inventory.route as r
              UNNEST r.schedule AS s
              JOIN `${couchbase.bucket.name}`.inventory.airline AS a ON KEYS r.airlineid
              WHERE r.sourceairport = ? AND r.destinationairport = ?
              AND s.day = ?
              ORDER BY a.name ASC"""

    val rows: Try[Seq[FlightPath]] = couchbase.cluster.query(statement,
      QueryOptions().parameters(QueryParameters.Positional(p.fromCode, p.toCode, p.dayOfWeek)))
      // We can retrieve the query results directly into our `FlightPath` case class
      .flatMap(result => result.rowsAs[FlightPath])

    rows.map(r => (statement, r))
  }
}

object FlightPathService {
  def findFromQuery(result: QueryResult, name: String, code: String): Try[String] = {
    val rows = result.rowsAs[JsonObject].get
    val filteredTry = rows.filter(row => row.containsKey(name))

    if (filteredTry.nonEmpty) Success(filteredTry.head.str(name))
    else Failure(new RuntimeException(s"Nothing for airport ${code} - please provide an airport's full name, e.g. 'San Franciso Intl'"))
  }
}