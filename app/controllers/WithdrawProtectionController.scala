/*
 * Copyright 2017 HM Revenue & Customs
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

package controllers

import auth.AuthorisedForPLA
import config.{FrontendAppConfig, FrontendAuthConnector}
import connectors.{KeyStoreConnector, PLAConnector}
import constructors.DisplayConstructors
import enums.ApplicationType
import models.ProtectionModel
import play.api.Logger
import play.api.Play.current
import play.api.i18n.Messages.Implicits._
import play.api.mvc.{Action, AnyContent}

object WithdrawProtectionController extends WithdrawProtectionController {
  val keyStoreConnector = KeyStoreConnector
  val displayConstructors = DisplayConstructors
  val plaConnector = PLAConnector
  override lazy val applicationConfig = FrontendAppConfig
  override lazy val authConnector = FrontendAuthConnector
  override lazy val postSignInRedirectUrl = FrontendAppConfig.ipStartUrl
}

trait WithdrawProtectionController extends BaseController with AuthorisedForPLA {

  val keyStoreConnector: KeyStoreConnector
  val displayConstructors: DisplayConstructors
  val plaConnector: PLAConnector

  /** Withdraw protections journey **/
  def withdrawSummary: Action[AnyContent] = AuthorisedByAny.async { implicit user =>
    implicit request =>
      keyStoreConnector.fetchAndGetFormData[ProtectionModel]("openProtection") map {
        case Some(currentProtection) =>
          Ok(views.html.pages.withdraw.withdrawSummary(displayConstructors.createWithdrawSummaryTable(currentProtection)))
        case _ =>
          Logger.warn(s"Could not retrieve protection data for user with nino ${user.nino} when loading the withdraw summary page")
          InternalServerError(views.html.pages.fallback.technicalError(ApplicationType.existingProtections.toString)).withHeaders(CACHE_CONTROL -> "no-cache")
      }
  }

}
