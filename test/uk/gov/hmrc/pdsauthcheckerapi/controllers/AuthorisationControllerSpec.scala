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

import play.api.test.Helpers
import play.api.test.Helpers._
import play.api.mvc._
import play.api.test.FakeRequest
import play.api.libs.json.Json
import uk.gov.hmrc.pdsauthcheckerapi.models.{Eori, PdsAuthRequest, PdsAuthResponse, PdsAuthResponseResult}
import uk.gov.hmrc.pdsauthcheckerapi.services.PdsService
import uk.gov.hmrc.pdsauthcheckerapi.controllers.AuthorisationController
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatestplus.mockito.MockitoSugar

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import java.time.LocalDate
import uk.gov.hmrc.pdsauthcheckerapi.actions.FakeAuthTypeAction
import org.mockito.Mockito._
import org.mockito.ArgumentMatchers.any
import org.apache.pekko.stream.testkit.NoMaterializer
import org.scalatest.matchers.must.Matchers.convertToAnyMustWrapper
import org.scalatest.time.SpanSugar.convertIntToGrainOfTime
import play.api.Configuration

class AuthorisationControllerSpec extends AnyWordSpecLike with Matchers with MockitoSugar {
  implicit val mat = NoMaterializer

  "AuthorisationController" should {

    "return OK with valid PdsAuthResponse for a valid request" in {
      val mockPdsService = mock[PdsService]
      val mockBodyParser = mock[BodyParsers.Default]
      val config: Configuration = Configuration("auth.supportedTypes" -> "UKIM")
      val mockAuthTypeAction = new FakeAuthTypeAction(mockBodyParser, config)

      val controllerComponents: ControllerComponents = Helpers.stubControllerComponents()
      val controller = new AuthorisationController(controllerComponents, mockPdsService, mockAuthTypeAction)

      val validRequest = PdsAuthRequest(Some(LocalDate.now()), "UKIM", Seq(Eori("GB123456789000")))
      val expectedResponse = PdsAuthResponse(
        LocalDate.now(),
        "UKIM",
        Seq(PdsAuthResponseResult(Eori("GB123456789000"), true, 0))
      )

      println(s"validRequest: ${Json.toJson(validRequest).toString()}")
      println(s"Expected Response: ${Json.toJson(expectedResponse).toString()}")


      when(mockPdsService.getValidatedCustoms(any[PdsAuthRequest])(any))
        .thenReturn(Future.successful(expectedResponse))

      val request = FakeRequest(POST, "/authorisations")
        .withHeaders(CONTENT_TYPE -> "application/json")
        .withBody(Json.toJson(validRequest))


      println(s"Request: $request")

      val result = controller.authorise()(request)

      println(contentAsJson(result))
      status(result) mustBe OK
      contentAsJson(result).as[PdsAuthResponse] shouldBe expectedResponse
    }

    "return BadRequest for an invalid auth type" in {
      val mockPdsService = mock[PdsService]
      val mockBodyParser = mock[BodyParsers.Default]
      val config: Configuration = Configuration("auth.supportedTypes" -> "InvalidAuthType")
      val mockAuthTypeAction = new FakeAuthTypeAction(mockBodyParser, config)

      val controllerComponents: ControllerComponents = Helpers.stubControllerComponents()
      val controller = new AuthorisationController(controllerComponents, mockPdsService, mockAuthTypeAction)

      val invalidRequest = PdsAuthRequest(Some(LocalDate.now()), "InvalidAuthType", Seq(Eori("GB123456789000")))

      val invalidAuthTypeResponse = Json.obj(
        "code" -> "INVALID_AUTHTYPE",
        "message" -> "Auth Type provided is not supported"
      )

      val request = FakeRequest(POST, "/authorisations")
        .withHeaders(CONTENT_TYPE -> "application/json")
        .withBody(Json.toJson(invalidRequest))

      val result = controller.authorise()(request)

      println(s"invalidRequest: ${Json.toJson(invalidRequest).toString()}")
      status(result) shouldBe BAD_REQUEST
      contentAsJson(result) shouldBe invalidAuthTypeResponse
    }
  }
}

