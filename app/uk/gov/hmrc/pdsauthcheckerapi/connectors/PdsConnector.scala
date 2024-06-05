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

import javax.inject.{Inject, Singleton}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.HttpReads.Implicits._

import scala.concurrent.{ExecutionContext, Future}
import play.api.libs.json._
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.pdsauthcheckerapi.models.{PdsAuthRequest, PdsAuthResponse}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import java.net.URL
@Singleton
class PdsConnector @Inject() (client: HttpClientV2, servicesConfig: ServicesConfig)(implicit ec: ExecutionContext){
  private val pdsEndpoint = new URL(servicesConfig.baseUrl("pds") + "/validatecustomsauth")

  def validateCustoms(pdsAuthRequest: PdsAuthRequest)(implicit hc: HeaderCarrier): Future[PdsAuthResponse] =
    client.post(pdsEndpoint).withBody(Json.toJson(pdsAuthRequest)).execute[PdsAuthResponse]

}
