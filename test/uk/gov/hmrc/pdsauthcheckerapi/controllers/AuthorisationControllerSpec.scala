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

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.http.Status
import play.api.libs.json.Json
import play.api.test.Helpers._
import play.api.test.{FakeRequest, Helpers}
import play.api.Configuration

class AuthorisationControllerSpec extends AnyWordSpec with Matchers {

  val config = Configuration("auth.supportedTypes" -> "UKIM")
  val controller = new AuthorisationController(Helpers.stubControllerComponents(), config);

  "AuthorisationController" should {

    "return NoContent (204) for supported auth type UKIM" in {
      val result = controller.authorise("UKIM")(FakeRequest(GET, "/authorisations/UKIM"))

      status(result) shouldBe Status.NO_CONTENT
    }

    "return BadRequest (400) for unsupported auth type" in {
      val result = controller.authorise("UNSUPPORTED")(FakeRequest(GET, "/authorisations/UNSUPPORTED"))

      status(result) shouldBe Status.BAD_REQUEST
      contentAsJson(result) shouldBe Json.obj(
        "code" -> "INVALID_AUTHTYPE",
        "message" -> "Auth Type provided is not supported"
      )
    }
  }
}
