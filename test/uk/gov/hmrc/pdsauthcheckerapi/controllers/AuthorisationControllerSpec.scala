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
import play.api.libs.json.Json
import play.api.mvc.{BodyParsers, Result}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.pdsauthcheckerapi.base.TestCommonGenerators
import uk.gov.hmrc.pdsauthcheckerapi.models.{PdsAuthRequest, PdsAuthResponse}
import uk.gov.hmrc.pdsauthcheckerapi.services.PdsService
import uk.gov.hmrc.pdsauthcheckerapi.actions.AuthTypeAction

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class AuthorisationControllerSpec extends AnyWordSpec with Matchers with TestCommonGenerators with ScalaFutures {

  val config = Configuration("auth.supportedTypes" -> Seq("UKIM"))
  val mockPdsService = mock[PdsService]
  val supportedAuthTypes = Set("UKIM")
  val bodyParsers = new BodyParsers.Default // Properly instantiate BodyParsers.Default
  val authTypeAction = new AuthTypeAction(bodyParsers, supportedAuthTypes)
  val controller = new AuthorisationController(Helpers.stubControllerComponents(), mockPdsService, authTypeAction)

  "AuthorisationController" should {

    "return 200 OK and return service layer response for supported auth type UKIM" in {
      val authRequestGen = authorisationRequestGen
      val responseGen = authRequestGen.flatMap(authorisationResponseGen)

      forAll(authRequestGen, responseGen) { (authRequest, serviceResponse) =>
        when(mockPdsService
          .getValidatedCustoms(ArgumentMatchers.eq(authRequest))(any[HeaderCarrier]))
          .thenReturn(Future.successful(serviceResponse))

        val request = FakeRequest().withBody(Json.toJson(authRequest)).withHeaders("Content-Type" -> "application/json")
        val result: Future[Result] = controller.authorise(request.map(_.as[PdsAuthRequest])) // Convert to expected type
        status(result) mustBe OK
        contentAsJson(result).as[PdsAuthResponse] shouldBe serviceResponse
      }
    }

    "return 400 BAD_REQUEST error message for unsupported auth type" in {
      val authRequest = authorisationRequestGen.sample.get.copy(authType = "UNSUPPORTED AUTH TYPE")
      val invalidAuthTypeResponse = Json.obj(
        "code" -> "INVALID_AUTHTYPE",
        "message" -> "Auth Type provided is not supported"
      )

      val request = FakeRequest().withBody(Json.toJson(authRequest)).withHeaders("Content-Type" -> "application/json")
      val result: Future[Result] = controller.authorise(request.map(_.as[PdsAuthRequest])) // Convert to expected type
      status(result) mustBe BAD_REQUEST
      contentAsJson(result) shouldBe invalidAuthTypeResponse
    }
  }
}
