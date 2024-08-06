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

package uk.gov.hmrc.pdsauthcheckerapi.config

import javax.inject.{Inject, Singleton}
import play.api.Configuration
import io.lemonlabs.uri.{Url, UrlPath}

@Singleton
case class AppConfig @Inject() (
    config: Configuration,
    servicesConfig: UKIMSServicesConfig
) {

  lazy val appName: String = config.get[String]("appName")

  lazy val eisBaseUrl = Url.parse(servicesConfig.baseUrl("eis"))

  lazy val eisUri =
    UrlPath.parse(config.get[String]("microservice.services.eis.uri"))

  lazy val authToken: String =
    config.get[String]("microservice.services.eis.authorisation.token")

}
