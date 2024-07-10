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

package uk.gov.hmrc.pdsauthcheckerapi.connectors
import com.github.tomakehurst.wiremock.client.WireMock._
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import play.api.Configuration
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.test.{HttpClientV2Support, WireMockSupport}
import uk.gov.hmrc.pdsauthcheckerapi.base.TestCommonGenerators
import uk.gov.hmrc.pdsauthcheckerapi.config.{AppConfig, UKIMSServicesConfig}
import uk.gov.hmrc.pdsauthcheckerapi.models.errors.InvalidAuthTokenPdsError
import uk.gov.hmrc.pdsauthcheckerapi.models.{
  Eori,
  PdsAuthResponse,
  PdsAuthResponseResult
}
import java.time.LocalDate
import scala.concurrent.ExecutionContext.Implicits.global

class PdsConnectorSpec
    extends AnyWordSpec
    with Matchers
    with ScalaFutures
    with HttpClientV2Support
    with TestCommonGenerators
    with IntegrationPatience
    with WireMockSupport {

  private val configuration = Configuration(
    "appName" -> "pds-auth-checker-api",
    "microservice.services.eis.authorisation.token" -> "Bearer mockBearerToken",
    "microservice.services.eis.host" -> wireMockHost,
    "microservice.services.eis.port" -> wireMockPort
  )

  private val mockUKIMSServicesConfig = new UKIMSServicesConfig(configuration)

  private val wiremockServerConfig = new AppConfig(
    configuration,
    mockUKIMSServicesConfig
  )

  private val pdsPath = "/validatecustomsauth"

  private val pdsConnector =
    new PdsConnector(httpClientV2, wiremockServerConfig)

  "PdsConnector" when {
    "an authorisation request is made" should {
      "return a successful response with body for a valid response to PDS" in {
        givenPdsReturns(
          200,
          pdsPath,
          s"""{
             |  "processingDate": "2021-01-01",
             |  "authType": "UKIM",
             |  "results": [
             |    {
             |      "eori": "GB120000000999",
             |      "valid": false,
             |      "code": 1
             |    },
             |    {
             |      "eori": "GB120001000919",
             |      "valid": true,
             |      "code": 0
             |    }
             |  ]
             |}""".stripMargin
        )

        val response = pdsConnector
          .validateCustoms(authorisationRequestGen.sample.get)(HeaderCarrier())
          .futureValue

        response shouldBe Right(
          PdsAuthResponse(
            LocalDate.of(2021, 1, 1),
            "UKIM",
            Seq(
              PdsAuthResponseResult(Eori("GB120000000999"), valid = false, 1),
              PdsAuthResponseResult(Eori("GB120001000919"), valid = true, 0)
            )
          )
        )
      }
      "return an appropriate error when authToken is rejected" in {
        givenPdsReturns(
          403,
          pdsPath,
          s"""{
             |  "timestamp": "2024-07-09T04:43:41.051892Z",
             |  "errorCode": "403",
             |  "errorMessage": "Authorisation not found",
             |  "sourcePDSFaultDetails": "uri=/pds/cnit/validatecustomsauth/v1"
             |}""".stripMargin
        )

        val response = pdsConnector
          .validateCustoms(authorisationRequestGen.sample.get)(HeaderCarrier())
          .futureValue

        response shouldBe Left(
          InvalidAuthTokenPdsError()
        )
      }
    }
  }
  private def givenPdsReturns(status: Int, url: String, body: String): Unit =
    wireMockServer.stubFor(
      post(urlEqualTo(url))
        .willReturn(
          aResponse()
            .withStatus(status)
            .withBody(body)
        )
    )
}
