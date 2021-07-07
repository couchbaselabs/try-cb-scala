package services

import com.couchbase.client.core.error.DocumentNotFoundException
import com.couchbase.client.scala.kv.LookupInSpec.get
import com.couchbase.client.scala.search.SearchOptions
import com.couchbase.client.scala.search.queries.SearchQuery
import com.couchbase.client.scala.search.result.ReactiveSearchResult
import components.CouchbaseConnection
import play.api.libs.json.{Json, OWrites}
import reactor.core.scala.publisher.{SFlux, SMono}

import javax.inject.Inject
import scala.util.{Failure, Success, Try}


case class Hotel(country: String, city: String, state: String, address: String, name: String, description: String) {
  def fullAddress = s"${address}, ${city}, ${state}, ${country}"
}

case class HotelsResult(context: Seq[String], data: Seq[Hotel])

object Hotel {
  implicit val playCodec: OWrites[Hotel] = Json.writes[Hotel]
}

object HotelsResult {
  implicit val playCodec: OWrites[HotelsResult] = Json.writes[HotelsResult]
}

class HotelsService @Inject()(val couchbase: CouchbaseConnection) {

  /** Retries all hotels matching a given location and optionally description.
   *
   * It uses Couchbase's Full Text Search (FTS) service for this.
   */
  def hotels(location: String, description: String): Try[HotelsResult] = {
    var fts = SearchQuery.conjuncts(SearchQuery.term("hotel").field("type"))

    if (location.nonEmpty && "*" != location) {
      fts = fts.and(SearchQuery.disjuncts(SearchQuery.matchPhrase(location).field("country"),
        SearchQuery.matchPhrase(location).field("city"),
        SearchQuery.matchPhrase(location).field("state"),
        SearchQuery.matchPhrase(location).field("address")))
    }

    if (description.nonEmpty && "*" != description) {
      fts = fts.and(SearchQuery.disjuncts(SearchQuery.matchPhrase(description).field("description"),
        SearchQuery.matchPhrase(description).field("name")))
    }

    val result: SMono[ReactiveSearchResult] = couchbase.cluster.reactive.searchQuery("hotels-index", fts, SearchOptions().limit(100))

    lookupDocuments(result).map(r =>
      HotelsResult(Seq("FTS search - scoped to: inventory.hotel within fields country, city, state, address, name, description"), r))
  }

  private def lookupDocuments(result: SMono[ReactiveSearchResult]): Try[Seq[Hotel]] = {
    val coll = couchbase.bucket.scope("inventory").collection("hotel").reactive

    // How many Key-Value lookups we want to perform in parallel.
    val concurrency = 100

    // We use the reactive API here, which lets us easily loop over the results of the FTS query, and do a Key-Value
    // fetch of each document in parallel.  There is also an API based around Scala `Future`, providing another way
    // to perform parallelism.
    val x: SFlux[Hotel] = result
      .flatMapMany(r =>
        // This .flatMap() will automatically parallelise the Key-Value lookups.
        r.rows.flatMap(row => {
          val y: SFlux[Hotel] = coll.lookupIn(row.id, Seq(get("country"),
            get("city"),
            get("state"),
            get("address"),
            get("name"),
            get("description")))
            .onErrorResume {
              case _: DocumentNotFoundException => SMono.empty
              case err => SMono.raiseError(err)
            }
            .flatMapMany(result => {
              val r: Try[Hotel] = for {
                country <- result.contentAs[String](0)
                city <- result.contentAs[String](1)
                state <- result.contentAs[String](2)
                address <- result.contentAs[String](3)
                name <- result.contentAs[String](4)
                description <- result.contentAs[String](5)
              } yield Hotel(country, city, state, address, name, description)

              r match {
                case Success(v) => SMono.just(v)
                case Failure(err) => SMono.raiseError(err)
              }
            })
          y
        }, maxConcurrency = concurrency))

    // Usually we would try and return the reactive chain to the user so we are asynchronously streaming results back,
    // but Play's reactive support is currently experimental, so instead just block synchronously.
    Try(x.collectSeq().block())
  }
}
