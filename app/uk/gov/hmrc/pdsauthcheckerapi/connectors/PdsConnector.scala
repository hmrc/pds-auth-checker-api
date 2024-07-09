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

import play.api.Logging
import play.api.http.Status.{FORBIDDEN, OK}

import javax.inject.{Inject, Singleton}
import uk.gov.hmrc.http.{
  HeaderCarrier,
  HttpResponse,
  StringContextOps
}
import uk.gov.hmrc.http.HttpReads.Implicits._

import scala.concurrent.{ExecutionContext, Future}
import play.api.libs.json._
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.pdsauthcheckerapi.config.AppConfig
import uk.gov.hmrc.pdsauthcheckerapi.models.errors.PdsErrorDetail
import uk.gov.hmrc.pdsauthcheckerapi.models.{PdsAuthRequest, PdsAuthResponse}

@Singleton
class PdsConnector @Inject() (client: HttpClientV2, appConfig: AppConfig)(
    implicit ec: ExecutionContext
) extends Logging {
  private val pdsEndpoint =
    appConfig.eisUrl.addPathParts("validatecustomsauth")
  private val authToken = appConfig.authToken

  def validateCustoms(
      pdsAuthRequest: PdsAuthRequest
  )(implicit
      hc: HeaderCarrier
  ): Future[Either[PdsErrorDetail, PdsAuthResponse]] =
    client
      .post(url"$pdsEndpoint")
      .withBody(Json.toJson(pdsAuthRequest))
      .setHeader("Authorization" -> "Bearer not real")
      .execute[HttpResponse]
      .flatMap { response =>
        response.status match {
          case OK =>
            Future.successful(Right(response.json.as[PdsAuthResponse]))
          case FORBIDDEN =>
            Future.successful(Left(response.json.as[PdsErrorDetail]))
          case _ =>
            logger.error(
              s"Did not receive OK from PDS - instead got ${response.status} and ${response.body}"
            )
            Future.failed(
              new RuntimeException(s"Unexpected status: ${response.status}")
            )
        }
      }
}
