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
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import uk.gov.hmrc.pdsauthcheckerapi.base.TestCommonGenerators
import uk.gov.hmrc.pdsauthcheckerapi.models.{
  AuthorisedBadRequestCode,
  EoriValidationError,
  ValidationErrorResponse
}
class ErrorConverterServiceSpec
    extends AnyWordSpec
    with Matchers
    with MockitoSugar
    with TestCommonGenerators {

  val errorConverterService = new ErrorConverterService()
  "convertValidationError" should {
    "Convert singular error into ValidationErrorResponse" in {
      val input = NonEmptyList.one(
        EoriValidationError(
          "GB123456789",
          "Invalid Format: Too few digits"
        )
      )
      val expectedResult = ValidationErrorResponse(
        AuthorisedBadRequestCode.InvalidFormat,
        "Input format for request data",
        Seq(
          EoriValidationError("GB123456789", "Invalid Format: Too few digits")
        )
      )
      errorConverterService.convertValidationError(
        input
      ) shouldBe expectedResult
    }
    "Convert multiple errors into ValidationErrorResponse" in {
      val input = NonEmptyList(
        EoriValidationError(
          "FR123456789",
          "Invalid Format: FR is not a supported country code"
        ),
        List(
          EoriValidationError("FR123456789", "Invalid Format: Too few digits")
        )
      )
      val expectedResult = ValidationErrorResponse(
        AuthorisedBadRequestCode.InvalidFormat,
        "Input format for request data",
        Seq(
          EoriValidationError(
            "FR123456789",
            "Invalid Format: FR is not a supported country code"
          ),
          EoriValidationError("FR123456789", "Invalid Format: Too few digits")
        )
      )
      errorConverterService.convertValidationError(
        input
      ) shouldBe expectedResult
    }
    "Convert EORI: Too many digits error into ValidationErrorResponse" in {
      val input = NonEmptyList.one(
        EoriValidationError(
          "GB1234567890123",
          "Invalid Format: Too many digits"
        )
      )
      val expectedResult = ValidationErrorResponse(
        AuthorisedBadRequestCode.InvalidFormat,
        "Input format for request data",
        Seq(
          EoriValidationError(
            "GB1234567890123",
            "Invalid Format: Too many digits"
          )
        )
      )
      errorConverterService.convertValidationError(
        input
      ) shouldBe expectedResult
    }
    "Convert EORI: Unsupported character error into ValidationErrorResponse" in {
      val input = NonEmptyList.one(
        EoriValidationError(
          "GB123456*89012",
          "Invalid Format: EORI must start with GB or XI and be followed by 12 digits"
        )
      )
      val expectedResult = ValidationErrorResponse(
        AuthorisedBadRequestCode.InvalidFormat,
        "Input format for request data",
        Seq(
          EoriValidationError(
            "GB123456*89012",
            "Invalid Format: EORI must start with GB or XI and be followed by 12 digits"
          )
        )
      )
      errorConverterService.convertValidationError(
        input
      ) shouldBe expectedResult
    }
  }

}
