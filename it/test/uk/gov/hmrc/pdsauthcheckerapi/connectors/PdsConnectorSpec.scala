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
import play.api.libs.json.{JsValue, Json}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.test.{HttpClientV2Support, WireMockSupport}
import uk.gov.hmrc.pdsauthcheckerapi.base.TestCommonGenerators
import uk.gov.hmrc.pdsauthcheckerapi.config.{AppConfig, UKIMSServicesConfig}
import uk.gov.hmrc.pdsauthcheckerapi.models.{Eori, PdsAuthResponse, PdsAuthResponseResult}

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
        val generatedData = generateAndStorePdsResponseBody()

        //println(s"Generated Response Body:\n${Json.prettyPrint(generatedData.responseBody)}")
        //println(s"Generated Auth Request:\n$generatedData.authRequest")

        givenPdsReturns(
          200,
          pdsPath,
          generatedData.responseBody.toString()
        )

        val response = pdsConnector
          .validateCustoms(generatedData.authRequest)(HeaderCarrier())
          .futureValue

        response shouldBe PdsAuthResponse(
          LocalDate.parse((lastGeneratedPdsResponse \ "processingDate").as[String]),
          (lastGeneratedPdsResponse \ "authType").as[String],
          (lastGeneratedPdsResponse \ "results").as[Seq[JsValue]].map { result =>
            PdsAuthResponseResult(
              Eori((result \ "eori").as[String]),
              (result \ "valid").as[Boolean],
              (result \ "code").as[Int]
            )
          }
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
