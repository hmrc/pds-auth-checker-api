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

import cats.data.{NonEmptyList, ValidatedNel}
import cats.syntax.apply._
import cats.syntax.traverse._
import cats.syntax.validated._
import play.api.Configuration

import javax.inject._
import play.api.libs.json.Json
import play.api.mvc.{Action, ControllerComponents, Request, Result}
import uk.gov.hmrc.pdsauthcheckerapi.controllers.AuthorisationController.{
  validateDate,
  validateEori
}
import uk.gov.hmrc.pdsauthcheckerapi.models.{
  AuthorisedBadRequestCode,
  AuthorisedBadRequestResponse,
  DateValidationError,
  Eori,
  EoriValidationError,
  PdsAuthRequest,
  UnvalidatedRequest,
  ValidationError
}
import uk.gov.hmrc.pdsauthcheckerapi.services.PdsService
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

@Singleton
class AuthorisationController @Inject() (
    cc: ControllerComponents,
    config: Configuration,
    pdsService: PdsService
)(implicit ec: ExecutionContext)
    extends BackendController(cc) {

  def authorise: Action[UnvalidatedRequest] =
    Action(parse.json[UnvalidatedRequest]).async {
      implicit request: Request[UnvalidatedRequest] =>
        if (!supportedAuthTypes.contains(request.body.authType)) {
          Future.successful(InvalidAuthTypeResponse)
        } else {

          val handleValidationErrors
              : NonEmptyList[ValidationError] => Future[Result] = errors =>
            Future.successful(
              BadRequest(
                Json.toJson(
                  AuthorisedBadRequestResponse(
                    AuthorisedBadRequestCode.InvalidFormat,
                    "Input format for request data",
                    errors.toList
                  )
                )
              )
            )

          val handleValidEorisAndDate
              : ((Option[LocalDate], Seq[Eori])) => Future[Result] = {
            case (date, eoris) =>
              pdsService
                .getValidatedCustoms(
                  PdsAuthRequest(date, request.body.authType, eoris)
                )
                .map { pdsAuthResponse =>
                  Ok(Json.toJson(pdsAuthResponse))
                }
          }

          val validatedEoris: ValidatedNel[EoriValidationError, Seq[Eori]] = {
            request.body.eoris.traverse(validateEori)
          }
          val validatedDate
              : ValidatedNel[DateValidationError, Option[LocalDate]] =
            request.body.validityDate.traverse(validateDate)

          val merged: ValidatedNel[
            ValidationError,
            (Option[LocalDate], Seq[Eori])
          ] =
            (
              validatedDate,
              validatedEoris
            ).tupled
          merged.fold(
            handleValidationErrors,
            handleValidEorisAndDate
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

object AuthorisationController {
  private def validateDate(
      rawDate: String
  ): ValidatedNel[DateValidationError, LocalDate] =
    Try(LocalDate.parse(rawDate, DateTimeFormatter.ISO_LOCAL_DATE)).fold(
      _ =>
        DateValidationError(
          rawDate,
          "Invalid Format: Dates must use ISO-8601 format YYYY-MM-DD"
        ).invalidNel,
      _.valid
    )

  private def validateEori(
      rawEori: String
  ): ValidatedNel[EoriValidationError, Eori] =
    (validateCountryCode(rawEori), validateEoriDigits(rawEori)).mapN((_, _) =>
      Eori(rawEori)
    )

  private def validateCountryCode(
      rawEori: String
  ): ValidatedNel[EoriValidationError, String] = {
    val allegedCountryCode = rawEori.takeWhile(!_.isDigit)
    if (validCountryCodes.contains(allegedCountryCode)) {
      rawEori.validNel
    } else {
      EoriValidationError(
        rawEori,
        invalidCountryCodeMessage(allegedCountryCode)
      ).invalidNel
    }
  }

  private val validCountryCodes = Seq("GB", "XI")

  private def invalidCountryCodeMessage(invalidCode: String): String =
    s"Invalid Format: $invalidCode is not a supported country code"

  private def validateEoriDigits(
      rawEori: String
  ): ValidatedNel[EoriValidationError, String] = {
    val allegedDigits = rawEori.dropWhile(!_.isDigit)

    val errors = List(
      if (allegedDigits.exists(!_.isDigit))
        Some(
          "Invalid Format: EORI must start with GB or XI and be followed by 12 digits"
        )
      else None,
      if (allegedDigits.length < 12)
        Some("Invalid Format: Too few digits")
      else None,
      if (allegedDigits.length > 12)
        Some("Invalid Format: Too many digits")
      else None
    ).flatten

    if (errors.nonEmpty) {
      NonEmptyList
        .fromList(errors.map(err => EoriValidationError(rawEori, err)))
        .get
        .invalid
    } else {
      rawEori.validNel
    }
  }

}
