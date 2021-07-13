package controllers

import play.api.mvc.{BaseController, ControllerComponents}

import javax.inject.Inject

class IndexController @Inject() (val controllerComponents: ControllerComponents) extends BaseController {
  def index = Action { implicit request =>
    Ok("Congratulations, the Couchbase Scala SDK backend for the travel sample application is now running.")
  }
}
