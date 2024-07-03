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
import play.api.Configuration

import javax.inject._
import play.api.libs.json.Json
import play.api.mvc.{Action, ControllerComponents, Request}
import uk.gov.hmrc.pdsauthcheckerapi.models.UnvalidatedPdsAuthRequest
import uk.gov.hmrc.pdsauthcheckerapi.services.{
  ErrorConverterService,
  PdsService,
  ValidationService
}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AuthorisationController @Inject() (
    cc: ControllerComponents,
    config: Configuration,
    pdsService: PdsService,
    validationService: ValidationService,
    errorConverterService: ErrorConverterService
)(implicit ec: ExecutionContext)
    extends BackendController(cc) {

  def authorise: Action[UnvalidatedPdsAuthRequest] =
    Action(parse.json[UnvalidatedPdsAuthRequest]).async {
      implicit request: Request[UnvalidatedPdsAuthRequest] =>
        if (!supportedAuthTypes.contains(request.body.authType)) {
          Future.successful(InvalidAuthTypeResponse)
        } else {
          validationService
            .validateRequest(request.body)
            .fold(
              validationErrors =>
                Future.successful(
                  BadRequest(
                    Json.toJson(
                      errorConverterService
                        .convertValidationError(validationErrors)
                    )
                  )
                ),
              validatedPdsRequest =>
                pdsService
                  .getValidatedCustoms(
                    validatedPdsRequest
                  )
                  .map { pdsAuthResponse =>
                    Ok(Json.toJson(pdsAuthResponse))
                  }
            )
        }
    }

  private val InvalidAuthTypeResponse = BadRequest(
    Json.obj(
      "code" -> "INVALID_AUTHTYPE",
      "message" -> "Auth Type provided is not supported"
    )
  )

  private val supportedAuthTypes: Set[String] =
    config.get[String]("auth.supportedTypes").split(",").map(_.trim).toSet
}
