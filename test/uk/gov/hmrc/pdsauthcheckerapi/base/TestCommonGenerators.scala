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

package uk.gov.hmrc.pdsauthcheckerapi.base

import org.scalacheck.Gen
import uk.gov.hmrc.pdsauthcheckerapi.models.{Eori, PdsAuthRequest, PdsAuthResponse, PdsAuthResponseResult}
import java.time.LocalDate
import java.time.temporal.ChronoUnit

trait TestCommonGenerators {
  lazy val validEoriPrefix = Gen.oneOf("GB", "XI")
  lazy val validEoriSuffix = Gen.listOfN(12, Gen.numChar).map(_.mkString)

  lazy val eoriGen: Gen[Eori] = for {
    prefix <- validEoriPrefix
    suffix <- validEoriSuffix
  } yield Eori(prefix + suffix)

  lazy val eorisGen: Gen[Seq[Eori]] = Gen.chooseNum(1, 3000).flatMap(n => Gen.listOfN(n, eoriGen))

  lazy val authorisationRequestGen: Gen[PdsAuthRequest] = for {
    eoris <- eorisGen
    now = LocalDate.now()
    date <- Gen.option(Gen.choose(now.minus(1, ChronoUnit.YEARS), now.plus(3, ChronoUnit.MONTHS)))
    authType = "UKIM"
  } yield PdsAuthRequest(date, authType, eoris)

  def authorisationResponseResultGen(eori: Eori): Gen[PdsAuthResponseResult] = {
    val isValid = Gen.oneOf(true, false).sample.get
    val code = if (isValid) 0 else Gen.oneOf(1, 2).sample.get
    PdsAuthResponseResult(eori, isValid, code)
  }

  def authorisationResponseResultsGen(eoris: Seq[Eori]): Gen[Seq[PdsAuthResponseResult]] =  {
    eoris.map { eori =>
      authorisationResponseResultGen(eori).sample.get
    }
  }

  def authorisationResponseGen(authRequest: PdsAuthRequest): Gen[PdsAuthResponse] =
    PdsAuthResponse(authRequest.validityDate.getOrElse(LocalDate.now()), authRequest.authType, authorisationResponseResultsGen(authRequest.eoris).sample.get)
}