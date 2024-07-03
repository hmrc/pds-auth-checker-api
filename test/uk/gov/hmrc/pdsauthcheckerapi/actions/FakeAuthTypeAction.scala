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

package uk.gov.hmrc.pdsauthcheckerapi.actions

import play.api.mvc._
import play.api.libs.json.Json
import uk.gov.hmrc.pdsauthcheckerapi.models.PdsAuthRequest
import javax.inject.{Inject, Named, Singleton}
import scala.concurrent.{ExecutionContext, Future}

case class FakeAuthTypeAction @Inject() (
  override val parser: BodyParsers.Default,
 @Named("supportedAuthTypes") supportedAuthTypes: Set[String]
)(implicit override val executionContext: ExecutionContext)
    extends AuthTypeAction(parser, supportedAuthTypes) {

  override def invokeBlock[A](request: Request[A], block: Request[A] => Future[Result]): Future[Result] =
    block(request)
}
