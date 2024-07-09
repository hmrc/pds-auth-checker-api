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

package uk.gov.hmrc.pdsauthcheckerapi.services

import cats.data.NonEmptyList
import cats.implicits.catsSyntaxValidatedId
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import uk.gov.hmrc.pdsauthcheckerapi.base.TestCommonGenerators
import uk.gov.hmrc.pdsauthcheckerapi.models.errors.EoriValidationError
import uk.gov.hmrc.pdsauthcheckerapi.models.{
  Eori,
  PdsAuthRequest,
  UnvalidatedPdsAuthRequest
}

class ValidationServiceSpec
    extends AnyWordSpec
    with Matchers
    with MockitoSugar
    with TestCommonGenerators {
  val validationService = new ValidationService()

  def authRequestToUnvalidatedRequest(
      authRequest: PdsAuthRequest
  ): UnvalidatedPdsAuthRequest = {
    UnvalidatedPdsAuthRequest(
      authRequest.validityDate.map(_.toString),
      authRequest.authType,
      authRequest.eoris.map(_.value)
    )
  }

  "Validate request" should {
    "return a validated PdsAuthRequest" in {
      val authorisationRequest = authorisationRequestGen.sample.get
      val input = authRequestToUnvalidatedRequest(authorisationRequest)
      val expectedResult = authorisationRequest.valid
      validationService.validateRequest(input) shouldBe expectedResult
    }
    "Return an invalidated Nel containing a singular Validation error" in {
      val authRequest = authorisationRequestGen.sample.get
        .copy(eoris = Seq(Eori("GB123456789")))
      val input = authRequestToUnvalidatedRequest(authRequest)
      val expectedResult = EoriValidationError(
        "GB123456789",
        "Invalid Format: Too few digits"
      ).invalidNel
      validationService.validateRequest(input) shouldBe expectedResult
    }
    "Return an invalidated Nel containing multiple Validation errors" in {
      val authRequest = authorisationRequestGen.sample.get
        .copy(eoris = Seq(Eori("FR123456789")))
      val input = authRequestToUnvalidatedRequest(authRequest)
      val expectedResult = NonEmptyList(
        EoriValidationError(
          "FR123456789",
          "Invalid Format: FR is not a supported country code"
        ),
        List(
          EoriValidationError("FR123456789", "Invalid Format: Too few digits")
        )
      ).invalid
      validationService.validateRequest(input) shouldBe expectedResult
    }
    "Return an invalidated Nel if EORI has too many digits" in {
      val authRequest = authorisationRequestGen.sample.get
        .copy(eoris = Seq(Eori("GB1234567891231")))
      val input = authRequestToUnvalidatedRequest(authRequest)
      val expectedResult = EoriValidationError(
        "GB1234567891231",
        "Invalid Format: Too many digits"
      ).invalidNel
      validationService.validateRequest(input) shouldBe expectedResult
    }
    "Return an invalidated Nel if EORI contains non-numeric characters after country code" in {
      val authRequest = authorisationRequestGen.sample.get
        .copy(eoris = Seq(Eori("GB123456*89012")))
      val input = authRequestToUnvalidatedRequest(authRequest)
      val expectedResult = EoriValidationError(
        "GB123456*89012",
        "Invalid Format: EORI must start with GB or XI and be followed by 12 digits"
      ).invalidNel
      validationService.validateRequest(input) shouldBe expectedResult
    }
  }
}
