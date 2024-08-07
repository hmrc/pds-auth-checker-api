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

package uk.gov.hmrc.pdsauthcheckerapi.utils

import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.pdsauthcheckerapi.models.constants.CustomHeaderNames

import java.util.UUID

trait HeaderCarrierExtensions {

  def generateCorrelationId()(implicit hc: HeaderCarrier): String =
    hc.headers(scala.Seq(CustomHeaderNames.xCorrelationId)) match {
      case Seq((_, id)) =>
        id
      case _ =>
        UUID.randomUUID().toString
    }
}
