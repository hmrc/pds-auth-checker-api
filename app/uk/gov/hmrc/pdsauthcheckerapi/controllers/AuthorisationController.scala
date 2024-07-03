/*
 * Copyright 2024 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.pdsauthcheckerapi.controllers


import javax.inject._
import play.api.libs.json.Json
import play.api.mvc._
import uk.gov.hmrc.pdsauthcheckerapi.models
import uk.gov.hmrc.pdsauthcheckerapi.models.PdsAuthRequest
import uk.gov.hmrc.pdsauthcheckerapi.services.PdsService
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.pdsauthcheckerapi.actions.AuthTypeAction

import scala.concurrent.ExecutionContext

@Singleton
class AuthorisationController @Inject()(
  cc: ControllerComponents,
  pdsService: PdsService,
  authTypeAction: AuthTypeAction,
)(implicit ec: ExecutionContext) extends BackendController(cc) {

  def authorise: Action[PdsAuthRequest] = authTypeAction.async(parse.json[models.PdsAuthRequest]) { implicit request =>
    val pdsAuthRequestBody = request.body
    pdsService.getValidatedCustoms(pdsAuthRequestBody).map { pdsAuthResponse =>
      Ok(Json.toJson(pdsAuthResponse))
    }
  }
}

