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

package uk.gov.hmrc.pdsauthcheckerapi.models

import play.api.libs.json.Writes
import play.api.libs.json.Writes.temporalWrites

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

object Iso8601DateTime {
  private val iso8601DateTimeFormat: String = "yyyy-MM-dd'T'HH:mm:ss.SS'Z'"
  private val iso8601DateTimeFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern(iso8601DateTimeFormat)
  implicit val iso8601DateTimeWrites: Writes[ZonedDateTime] =
    temporalWrites[ZonedDateTime, DateTimeFormatter](iso8601DateTimeFormatter)
}
