package controllers

import com.couchbase.client.scala.json.JsonObject
import com.couchbase.client.scala.query.{QueryOptions, QueryParameters, QueryResult}
import components.CouchbaseConnection
import play.api.libs.json.{JsArray, JsObject, JsValue, Json, OWrites, Writes}
import play.api.mvc._
import reactor.core.scala.publisher.SFlux
import services.{AirportService, AirportsResult, BookFlightResult, FlightPathService, Hotel, HotelsService, TenantService, TokenService}

import javax.inject._
import scala.util.{Failure, Success, Try}
import java.text.DateFormat
import java.util.Calendar
import java.util.Locale

/** Handle any requests to the '/api' endpoint.  In a production application you'd likely want one controller for
 * flights, airports, hotels etc., but given the limited number of endpoints we combine them here.
 */
class ApiController @Inject()(val controllerComponents: ControllerComponents,
                              val airportService: AirportService,
                              val flightPathService: FlightPathService,
                              val hotelsService: HotelsService,
                              val tenantService: TenantService,
                              val tokenService: TokenService) extends BaseController {

  def airports() = Action { implicit request =>
    val search = request.queryString("search").head
    airportService.airports(search) match {
      case Success(v) => Ok(Json.toJson(v))
      case Failure(err) => InternalServerError(err.getMessage)
    }
  }

  def flightPaths(fromLoc: String, toLoc: String) = Action { implicit request =>
    val leave = request.queryString("leave").head
    val calendar = Calendar.getInstance(Locale.US)
    calendar.setTime(DateFormat.getDateInstance(DateFormat.SHORT, Locale.US).parse(leave))

    flightPathService.flightPaths(fromLoc, toLoc, calendar) match {
      case Success(v) => Ok(Json.toJson(v))
      case Failure(err) => InternalServerError(err.getMessage)
    }
  }

  def hotels(location: String, description: String) = Action { implicit request =>
    hotelsService.hotels(location, description) match {
      case Success(v) => Ok(Json.toJson(v))
      case Failure(err) => InternalServerError(err.getMessage)
    }
  }

  def signup(tenant: String) = Action { implicit request =>
    val json: JsValue = request.body.asJson.get
    val username = json("user").as[String]
    val password = json("password").as[String]

    tenantService.signup(tenant, username, password) match {
      case Success(v) => Ok(Json.toJson(v))
      case Failure(err) => InternalServerError(err.getMessage)
    }
  }

  def login(tenant: String) = Action { implicit request =>
    val json: JsValue = request.body.asJson.get
    val username = json("user").as[String]
    val password = json("password").as[String]

    tenantService.login(tenant, username, password) match {
      case Success(v) => Ok(Json.toJson(v))
      case Failure(err) => InternalServerError(err.getMessage)
    }
  }

  def bookFlight(tenant: String, username: String) = Action { implicit request =>
    val json: JsValue = request.body.asJson.get
    val flights: JsArray = json("flights").as[JsArray]
    val authHeader = Try(request.headers.get("Authorization").get)

    val result: Try[BookFlightResult] = authHeader.flatMap(ah =>
      tokenService.verifyAuthenticationHeader(ah, username))
      .flatMap(_ => tenantService.bookFlight(tenant, username, flights))

    result match {
      case Success(v) => Ok(Json.toJson(v))
      case Failure(err) => InternalServerError(err.getMessage)
    }
  }

  def flights(tenant: String, username: String) = Action { implicit request =>
    val authHeader = Try(request.headers.get("Authorization").get)

    val result: Try[BookFlightResult] = authHeader.flatMap(ah =>
      tokenService.verifyAuthenticationHeader(ah, username))
      .flatMap(_ => tenantService.getFlights(tenant, username))

    result match {
      case Success(v) => Ok(Json.toJson(v))
      case Failure(err) => InternalServerError(err.getMessage)
    }
  }
}
