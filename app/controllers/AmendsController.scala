/*
 * Copyright 2016 HM Revenue & Customs
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
import common.Strings
import config.{FrontendAppConfig, FrontendAuthConnector}
import connectors.KeyStoreConnector
import constructors.DisplayConstructors
import enums.ApplicationType
import forms.AmendUKPensionForm._
import models.amendModels.{AmendProtectionModel, AmendedUKPensionModel}
import models.ProtectionModel
import play.api.Logger
import play.api.mvc.{Action, AnyContent}
import uk.gov.hmrc.play.frontend.controller.FrontendController
import views.html.pages

import scala.concurrent.Future

object AmendsController extends AmendsController{
  val keyStoreConnector = KeyStoreConnector
  val displayConstructors = DisplayConstructors
  override lazy val applicationConfig = FrontendAppConfig
  override lazy val authConnector = FrontendAuthConnector
  override lazy val postSignInRedirectUrl = FrontendAppConfig.ipStartUrl
}

trait AmendsController  extends FrontendController with AuthorisedForPLA {

  val keyStoreConnector: KeyStoreConnector
  val displayConstructors: DisplayConstructors

  def amendsSummary(protectionType: String, status: String) = AuthorisedByAny.async { implicit user => implicit request =>
    val protectionKey = Strings.keyStoreAmendFetchString(protectionType, status)
    keyStoreConnector.fetchAndGetFormData[AmendProtectionModel](protectionKey).map {
      case Some(amendModel) => Ok(views.html.pages.amends.amendSummary(displayConstructors.createAmendDisplayModel(amendModel)))
      case _ => InternalServerError(views.html.pages.fallback.technicalError(ApplicationType.existingProtections.toString)).withHeaders(CACHE_CONTROL -> "no-cache")
    }
  }

  val amendCurrentUKPension = AuthorisedByAny.async { implicit user => implicit request =>
    keyStoreConnector.fetchAndGetFormData[AmendProtectionModel]("amendedProtection").map {
      case Some(data) =>
        val currentUKPensionAmt = AmendedUKPensionModel(Some(data.updatedProtection.uncrystallisedRights.get))
        Ok(pages.amends.amendIP16CurrentUKPension(amendUKPensionForm.fill(currentUKPensionAmt)))
      case _ =>
        Logger.error(s"Could not retrieve amend protection model for user with nino ${user.nino} when loading the amend current UK pension page")
        InternalServerError(views.html.pages.fallback.technicalError(ApplicationType.existingProtections.toString)).withHeaders(CACHE_CONTROL -> "no-cache")
    }
  }

  def amendPensionsTakenBefore(protectionType: String, status: String) = AuthorisedByAny.async { implicit user => implicit request =>
    Future.successful(Ok)
  }

  def amendPensionsTakenBetween(protectionType: String, status: String) = AuthorisedByAny.async { implicit user => implicit request =>
    Future.successful(Ok)
  }

  def amendOverseasPensions(protectionType: String, status: String) = AuthorisedByAny.async { implicit user => implicit request =>
    Future.successful(Ok)
  }

  def amendCurrentPensions(protectionType: String, status: String) = AuthorisedByAny.async { implicit user => implicit request =>
    Future.successful(Ok)
  }


  val submitAmendCurrentUKPension = AuthorisedByAny.async { implicit user => implicit request =>

      amendUKPensionForm.bindFromRequest.fold(
      errors => Future.successful(BadRequest(pages.amends.amendIP16CurrentUKPension(errors))),
      success => {
        keyStoreConnector.fetchAndGetFormData[AmendProtectionModel]("amendedProtection").map{
          case Some(model) =>
            val updated = model.updatedProtection.copy(uncrystallisedRights = Some(success.amendedUKPensionAmt.get.toDouble))
            val amendModel = AmendProtectionModel(model.originalProtection, updated)
            keyStoreConnector.saveFormData[AmendProtectionModel]("amendedProtection", amendModel)
            Redirect(routes.AmendsController.amendsSummary(updated.protectionType.get, updated.status.get))
          case _ =>
            Logger.error(s"Could not retrieve amend protection model for user with nino ${user.nino} when amending current UK pension")
            InternalServerError(views.html.pages.fallback.technicalError(ApplicationType.IP2016.toString)).withHeaders(CACHE_CONTROL -> "no-cache")
        }
      }
    )
    Future.successful(Ok)
  }

}
