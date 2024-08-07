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

import com.typesafe.config.Config
import org.apache.pekko.actor.ActorSystem
import play.api.Logging
import play.api.http.{HeaderNames, MimeTypes}
import play.api.http.Status.{FORBIDDEN, OK}

import javax.inject.{Inject, Singleton}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, Retries, StringContextOps}
import uk.gov.hmrc.http.HttpReads.Implicits._

import scala.concurrent.{ExecutionContext, Future}
import play.api.libs.json._
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.pdsauthcheckerapi.config.AppConfig
import uk.gov.hmrc.pdsauthcheckerapi.models.constants.{
  CustomHeaderNames,
  HeaderValues
}
import uk.gov.hmrc.pdsauthcheckerapi.models.errors.{
  InvalidAuthTokenPdsError,
  ParseResponseFailure,
  PdsError,
  PdsErrorDetail
}
import uk.gov.hmrc.pdsauthcheckerapi.models.{
  PdsAuthRequest,
  PdsAuthResponse,
  Rfc7231DateTime
}
import uk.gov.hmrc.pdsauthcheckerapi.utils.HeaderCarrierExtensions

@Singleton
class PdsConnector @Inject() (
    client: HttpClientV2,
    appConfig: AppConfig,
    override val configuration: Config,
    override val actorSystem: ActorSystem
)(implicit
    ec: ExecutionContext
) extends Logging
    with Retries
    with HeaderCarrierExtensions {

  private val pdsEndpoint =
    appConfig.eisBaseUrl.withPath(appConfig.eisUri)

  private val authToken = appConfig.authToken

  def validateCustoms(
      pdsAuthRequest: PdsAuthRequest
  )(implicit
      hc: HeaderCarrier
  ): Future[Either[PdsError, PdsAuthResponse]] = {
    client
      .post(url"$pdsEndpoint")
      .setHeader(integrationFrameworkHeaders: _*)
      .withBody(Json.toJson(pdsAuthRequest))
      .execute[HttpResponse]
      .flatMap { response =>
        response.status match {
          case OK        => handleResponse(response)
          case FORBIDDEN => handleForbidden(response)
          case _ =>
            logger.warn(
              s"Did not receive OK from PDS - instead got ${response.status} and ${response.body}"
            )
            Future.failed(
              new RuntimeException(s"Unexpected status: ${response.status}")
            )
        }
      }
  }

  private def integrationFrameworkHeaders(implicit
      hc: HeaderCarrier
  ): Seq[(String, String)] =
    Seq(
      (CustomHeaderNames.xCorrelationId, generateCorrelationId()),
      (HeaderNames.DATE, Rfc7231DateTime.now),
      (HeaderNames.CONTENT_TYPE, HeaderValues.JsonCharsetUtf8),
      (HeaderNames.ACCEPT, MimeTypes.JSON),
      (HeaderNames.AUTHORIZATION, s"Bearer $authToken")
    )

  private def handleResponse(
      response: HttpResponse
  ): Future[Either[PdsError, PdsAuthResponse]] = {
    response.json.validate[PdsAuthResponse] match {
      case JsSuccess(result, _) => Future.successful(Right(result))
      case JsError(errors) =>
        logger.warn(
          s"Unable to validate successful response - with the following errors - $errors"
        )
        Future.successful(Left(ParseResponseFailure()))
    }
  }

  private def handleForbidden(
      response: HttpResponse
  ): Future[Either[PdsError, PdsAuthResponse]] = {
    logger.error(
      s"PDS has rejected bearer token with the following: ${response.status} and ${response.body}"
    )
    response.json.validate[PdsErrorDetail] match {
      case JsSuccess(_, _) =>
        Future.successful(Left(InvalidAuthTokenPdsError()))
      case JsError(errors) =>
        logger.warn(s"Unable to parse PDS error response: $errors")
        Future.successful(Left(ParseResponseFailure()))
    }
  }
}
