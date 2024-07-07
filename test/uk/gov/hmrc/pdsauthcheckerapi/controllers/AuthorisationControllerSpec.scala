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

import cats.data.NonEmptyList
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.must.Matchers.convertToAnyMustWrapper
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar.mock
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks.forAll
import play.api.test.Helpers._
import play.api.test.{FakeRequest, Helpers}
import play.api.Configuration
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.Result
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.pdsauthcheckerapi.base.TestCommonGenerators
import uk.gov.hmrc.pdsauthcheckerapi.models.{
  AuthorisedBadRequestCode,
  Eori,
  EoriValidationError,
  ErrorDetail,
  PdsAuthRequest,
  PdsAuthResponse,
  UnvalidatedPdsAuthRequest,
  ValidationErrorResponse
}
import uk.gov.hmrc.pdsauthcheckerapi.services.{
  ErrorConverterService,
  PdsService,
  ValidationService
}
import cats.syntax.validated._
import uk.gov.hmrc.auth.core.{
  AuthConnector,
  AuthorisationException,
  NoActiveSession
}
import uk.gov.hmrc.auth.core.authorise.Predicate
import uk.gov.hmrc.auth.core.retrieve.Retrieval

import java.time.{Clock, Instant, ZoneOffset}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class AuthorisationControllerSpec
    extends AnyWordSpec
    with Matchers
    with TestCommonGenerators
    with ScalaFutures {

  trait Setup {
    val config: Configuration = Configuration("auth.supportedTypes" -> "UKIM")
    val fixedClock: Clock = Clock.fixed(Instant.now(), ZoneOffset.UTC)
    val mockPdsService: PdsService = mock[PdsService]
    val mockAuthConnector: AuthConnector = mock[AuthConnector]
    val mockValidationService: ValidationService = mock[ValidationService]
    val mockErrorConverterService: ErrorConverterService =
      mock[ErrorConverterService]
    val controller = new AuthorisationController(
      Helpers.stubControllerComponents(),
      config,
      mockPdsService,
      mockValidationService,
      mockErrorConverterService,
      fixedClock,
      mockAuthConnector
    )
  }
  def createValidationError(validationError: JsObject): JsObject = {
    Json.obj(
      "code" -> "INVALID_FORMAT",
      "message" -> "Input format for request data",
      "validationErrors" -> Json.arr(validationError)
    )
  }
  def authRequestToUnvalidatedRequest(
      authRequest: PdsAuthRequest
  ): UnvalidatedPdsAuthRequest = {
    UnvalidatedPdsAuthRequest(
      authRequest.validityDate.map(_.toString),
      authRequest.authType,
      authRequest.eoris.map(_.value)
    )
  }
  "AuthorisationController" should {

    "return 200 OK and return service layer response for supported auth type UKIM" in new Setup {
      val authRequestGen = authorisationRequestGen
      val responseGen = authRequestGen.flatMap(authorisationResponseGen)
      when(
        mockAuthConnector
          .authorise(any[Predicate](), any[Retrieval[Unit]]())(any(), any())
      )
        .thenReturn(Future.successful(()))

      forAll(authRequestGen, responseGen) { (authRequest, serviceResponse) =>
        when(
          mockValidationService.validateRequest(
            ArgumentMatchers.eq(authRequestToUnvalidatedRequest(authRequest))
          )
        ).thenReturn(authRequest.validNel)
        when(
          mockPdsService
            .getValidatedCustoms(ArgumentMatchers.eq(authRequest))(
              any[HeaderCarrier]
            )
        )
          .thenReturn(Future.successful(serviceResponse))
        val request =
          FakeRequest().withBody(authRequestToUnvalidatedRequest(authRequest))
        val result: Future[Result] = controller.authorise(request)
        status(result) mustBe OK
        contentAsJson(result).as[PdsAuthResponse] shouldBe serviceResponse
      }
    }
    "return 401 UNAUTHORIZED when there is no active session" in new Setup {
      when(mockAuthConnector.authorise(any(), any())(any(), any()))
        .thenReturn(Future.failed(new NoActiveSession("No active session") {}))
      val authRequestGen = authorisationRequestGen

      forAll(authRequestGen) { authRequest: PdsAuthRequest =>
        val request =
          FakeRequest().withBody(authRequestToUnvalidatedRequest(authRequest))
        val result: Future[Result] = controller.authorise(request)
        status(result) mustBe UNAUTHORIZED
        contentAsString(result) mustBe Json
          .toJson(
            ErrorDetail(
              fixedClock.instant(),
              "401",
              "You are not allowed to access this resource",
              "uri=/pds/cnit/validatecustomsauth/v1"
            )
          )
          .toString
      }
    }

    "return 403 FORBIDDEN when user is not authorised" in new Setup {
      when(mockAuthConnector.authorise(any(), any())(any(), any()))
        .thenReturn(Future.failed(new AuthorisationException("Forbidden") {}))
      val authRequestGen = authorisationRequestGen

      forAll(authRequestGen) { authRequest: PdsAuthRequest =>
        val request =
          FakeRequest().withBody(authRequestToUnvalidatedRequest(authRequest))
        val result: Future[Result] = controller.authorise(request)
        status(result) mustBe FORBIDDEN
        contentAsString(result) mustBe Json
          .toJson(
            ErrorDetail(
              fixedClock.instant(),
              "403",
              "Authorisation not found",
              "uri=/pds/cnit/validatecustomsauth/v1"
            )
          )
          .toString
      }
    }

    "return 400 BAD_REQUEST error message for unsupported auth type" in new Setup {
      val authRequest = authorisationRequestGen.sample.get
        .copy(authType = "UNSUPPORTED AUTH TYPE")
      val invalidAuthTypeResponse = Json.obj(
        "code" -> "INVALID_AUTHTYPE",
        "message" -> "Auth Type provided is not supported"
      )
      when(
        mockAuthConnector
          .authorise(any[Predicate](), any[Retrieval[Unit]]())(any(), any())
      )
        .thenReturn(Future.successful(()))

      val request =
        FakeRequest().withBody(authRequestToUnvalidatedRequest(authRequest))
      val result: Future[Result] = controller.authorise(request)
      status(result) mustBe BAD_REQUEST
      contentAsJson(result) shouldBe invalidAuthTypeResponse
    }

    "return 400 BAD_REQUEST error message for incorrectly formatted EORI" in new Setup {
      val authRequest = authorisationRequestGen.sample.get
        .copy(eoris = Seq(Eori("GB123456789")))
      val validationErrors = Json.obj(
        "eori" -> "GB123456789",
        "validationError" -> "Invalid Format: Too few digits"
      )
      val eoriValidationError =
        EoriValidationError("GB123456789", "Invalid Format: Too few digits")
      val validationErrorResponse =
        ValidationErrorResponse(
          AuthorisedBadRequestCode.InvalidFormat,
          "Input format for request data",
          NonEmptyList.one(eoriValidationError).toList
        )
      when(
        mockAuthConnector
          .authorise(any[Predicate](), any[Retrieval[Unit]]())(any(), any())
      )
        .thenReturn(Future.successful(()))
      when(
        mockValidationService.validateRequest(
          ArgumentMatchers.eq(authRequestToUnvalidatedRequest(authRequest))
        )
      ).thenReturn(eoriValidationError.invalidNel)
      when(
        mockErrorConverterService.convertValidationError(
          ArgumentMatchers.eq(NonEmptyList.one(eoriValidationError))
        )
      ).thenReturn(validationErrorResponse)

      val request =
        FakeRequest().withBody(authRequestToUnvalidatedRequest(authRequest))
      val result: Future[Result] = controller.authorise(request)
      status(result) mustBe BAD_REQUEST
      contentAsJson(result) shouldBe createValidationError(validationErrors)
    }
  }
}
