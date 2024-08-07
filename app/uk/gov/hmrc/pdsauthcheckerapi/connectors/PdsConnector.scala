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

    val correlationId = generateCorrelationId()
    val date = Rfc7231DateTime.now

    val logMessage =
      s"""|pds-auth-checker-api
          |
          |Posting to PDS via EIS
          |
          |Submission Headers
          |
          |${CustomHeaderNames.xCorrelationId}: $correlationId
          |${HeaderNames.DATE}: $date
          |${HeaderNames.CONTENT_TYPE}: ${HeaderValues.JsonCharsetUtf8}
          |${HeaderNames.ACCEPT}: ${MimeTypes.JSON}
          |""".stripMargin

    val newHeaders = Seq(
      (CustomHeaderNames.xCorrelationId, correlationId),
      (HeaderNames.DATE, date),
      (HeaderNames.CONTENT_TYPE, HeaderValues.JsonCharsetUtf8),
      (HeaderNames.ACCEPT, MimeTypes.JSON),
      (HeaderNames.AUTHORIZATION, s"Bearer $authToken")
    )

    client
      .post(url"$pdsEndpoint")
      .setHeader(newHeaders: _*)
      .withBody(Json.toJson(pdsAuthRequest))
      .transform(r => {
        logger.info(logMessage)
        r
      })
      .execute[HttpResponse]
      .flatMap { response =>
        response.status match {
          case OK        => handleResponse(response)
          case FORBIDDEN => handleRejected(response)
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

  private def handleRejected(
      response: HttpResponse
  ): Future[Either[PdsError, PdsAuthResponse]] = {
    logger.error(
      s"PDS rjected"
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
