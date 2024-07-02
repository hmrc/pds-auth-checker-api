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
import play.api.test.FakeRequest
import play.api.libs.json.Json
import uk.gov.hmrc.pdsauthcheckerapi.models.{PdsAuthRequest, PdsAuthResponse, PdsAuthResponseResult, Eori}
import uk.gov.hmrc.pdsauthcheckerapi.services.PdsService
import uk.gov.hmrc.pdsauthcheckerapi.actions.AuthTypeAction
import uk.gov.hmrc.pdsauthcheckerapi.controllers.AuthorisationController
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatestplus.mockito.MockitoSugar
import scala.concurrent.Future
import java.time.LocalDate

class AuthorisationControllerSpec extends AnyWordSpecLike with Matchers with MockitoSugar {

  "AuthorisationController" should {

    "return OK with valid PdsAuthResponse for a valid request" in {
      val mockPdsService = mock[PdsService]
      val mockBodyParser = stubControllerComponents().parsers.defaultBodyParser
      val mockAuthTypeAction = new AuthTypeAction(mockBodyParser, Set("ValidAuthType"))

      val controller = new AuthorisationController(stubControllerComponents(), mockPdsService, mockAuthTypeAction)

      val validRequest = PdsAuthRequest(Some(LocalDate.now()), "ValidAuthType", Seq(Eori("GB123456789000")))
      val expectedResponse = PdsAuthResponse(
        LocalDate.now(),
        "ValidAuthType",
        Seq(PdsAuthResponseResult(Eori("GB123456789000"), true, 0))
      )

      org.scalatestplus.when(mockPdsService.getValidatedCustoms(any[PdsAuthRequest])(any[HeaderCarrier]))
        .thenReturn(Future.successful(expectedResponse))

      val request = FakeRequest(POST, "/authorise")
        .withHeaders(CONTENT_TYPE -> "application/json")
        .withBody(Json.toJson(validRequest))

      val result = controller.authorise()(request)

      status(result) shouldBe OK
      contentAsJson(result) shouldBe Json.toJson(expectedResponse)
    }

    "return BadRequest for an invalid auth type" in {
      val mockPdsService = mock[PdsService]
      val mockBodyParser = stubControllerComponents().parsers.defaultBodyParser
      val mockAuthTypeAction = new AuthTypeAction(mockBodyParser, Set("ValidAuthType"))

      val controller = new AuthorisationController(stubControllerComponents(), mockPdsService, mockAuthTypeAction)

      val invalidRequest = PdsAuthRequest(Some(LocalDate.now()), "InvalidAuthType", Seq(Eori("GB123456789000")))

      val request = FakeRequest(POST, "/authorise")
        .withHeaders(CONTENT_TYPE -> "application/json")
        .withBody(Json.toJson(invalidRequest))

      val result = controller.authorise()(request)

      status(result) shouldBe BAD_REQUEST
      contentAsJson(result) shouldBe Json.obj(
        "code" -> "INVALID_AUTHTYPE",
        "message" -> "Auth Type provided is not supported"
      )
    }
  }
}
