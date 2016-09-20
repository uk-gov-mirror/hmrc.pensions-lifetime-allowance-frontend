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

import auth.{AuthorisedForPLA, PLAUser}
import config.{FrontendAppConfig, FrontendAuthConnector}
import connectors.{KeyStoreConnector, PLAConnector}
import constructors.{ExistingProtectionsConstructor, ResponseConstructors}
import enums.ApplicationType.ApplicationType
import enums.{ApplicationOutcome, ApplicationType}
import models._
import play.api.Logger
import play.api.libs.json.Json
import play.api.mvc._
import uk.gov.hmrc.http.cache.client.CacheMap
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.frontend.controller.FrontendController
import uk.gov.hmrc.play.http._
import utils.Constants
import views.html.pages.result._

import scala.concurrent.Future
import scala.util.{Failure, Success}


object ResultController extends ResultController with ServicesConfig {
  override val keyStoreConnector = KeyStoreConnector
  override lazy val applicationConfig = FrontendAppConfig
  override lazy val authConnector = FrontendAuthConnector
  override lazy val postSignInRedirectUrl = FrontendAppConfig.confirmFPUrl

  override val plaConnector = PLAConnector
  override val responseConstructors = ResponseConstructors
}

trait ResultController extends FrontendController with AuthorisedForPLA {

  val keyStoreConnector: KeyStoreConnector
  val plaConnector: PLAConnector
  val responseConstructors: ResponseConstructors


  val processFPApplication = AuthorisedByAny.async {
    implicit user => implicit request =>
      implicit val protectionType = ApplicationType.FP2016
      plaConnector.applyFP16(user.nino.get).flatMap (
        response => routeViaMCNeededCheck (response)
      )
  }


  val processIPApplication = AuthorisedByAny.async {
    implicit user => implicit request =>
      implicit val protectionType = ApplicationType.IP2016
      for {
        userData <- keyStoreConnector.fetchAllUserData
        applicationResult <- plaConnector.applyIP16(user.nino.get, userData.get)
        response <- routeViaMCNeededCheck(applicationResult)
      } yield response
  }


  val processIP14Application = AuthorisedByAny.async {
    implicit user => implicit request =>
      implicit val protectionType = ApplicationType.IP2014
      for {
        userData <- keyStoreConnector.fetchAllUserData
        applicationResult <- plaConnector.applyIP14(user.nino.get, userData.get)
        response <- routeViaMCNeededCheck(applicationResult)
      } yield response
  }


  private def routeViaMCNeededCheck(response: HttpResponse)(implicit request: Request[AnyContent], user: PLAUser, protectionType: ApplicationType.Value): Future[Result] = {
    response.status match {
      case 423 => Future.successful(Locked(manualCorrespondenceNeeded()))
      case _ => saveAndRedirectToDisplay(response)
    }
  }


  private def saveAndRedirectToDisplay(response: HttpResponse)(implicit request: Request[AnyContent], user: PLAUser, protectionType: ApplicationType.Value): Future[Result] = {

    responseConstructors.createApplyResponseModelFromJson(response.json).map {
      model => keyStoreConnector.saveData[ApplyResponseModel](common.Strings.nameString("applyResponseModel"), model).map {
        cacheMap => protectionType match {
          case ApplicationType.IP2016 => Redirect(routes.ResultController.displayIP16())
          case ApplicationType.IP2014 => Redirect(routes.ResultController.displayIP14())
          case ApplicationType.FP2016 => Redirect(routes.ResultController.displayFP16())
        }
      }
    }.getOrElse {
      Logger.error(s"Unable to create ApplyResponseModel from application response for ${protectionType.toString} for user nino: ${user.nino.getOrElse("No NINO recorded")}")
      Future.successful(InternalServerError(views.html.pages.fallback.technicalError(protectionType.toString)).withHeaders(CACHE_CONTROL -> "no-cache"))
    }
  }


  val displayIP16 = displayResult(ApplicationType.IP2016)
  val displayIP14 = displayResult(ApplicationType.IP2014)
  val displayFP16 = displayResult(ApplicationType.FP2016)


  def displayResult(implicit protectionType: ApplicationType.Value): Action[AnyContent] = AuthorisedByAny.async {

    implicit user => implicit request=>
    val errorResponse = InternalServerError(views.html.pages.fallback.technicalError(protectionType.toString)).withHeaders(CACHE_CONTROL -> "no-cache")
    keyStoreConnector.fetchAndGetFormData[ApplyResponseModel](common.Strings.nameString("applyResponseModel")).map {
        case Some(model) => applicationOutcome(model) match {

          case ApplicationOutcome.Successful =>
            val displayModel = ResponseConstructors.createSuccessDisplayModel(model)
            Ok(resultSuccess(displayModel))

          case ApplicationOutcome.Rejected =>
            val displayModel = ResponseConstructors.createRejectionDisplayModel(model)
            Ok(resultRejected(displayModel))
        }
        case _ => errorResponse
    }

  }


  def applicationOutcome(model: ApplyResponseModel)(implicit user: PLAUser, protectionType: ApplicationType.Value): ApplicationOutcome.Value = {
    val notificationId = model.protection.notificationId
    assert(notificationId.isDefined, s"no notification ID returned in $protectionType application response for user nino ${user.nino}")
    val successCodes = protectionType match {
      case ApplicationType.FP2016 => Constants.successCodes
      case ApplicationType.IP2016 => Constants.ip16SuccessCodes
      case ApplicationType.IP2014 => Constants.ip14SuccessCodes
    }
    if (successCodes.contains(notificationId.get)) ApplicationOutcome.Successful else ApplicationOutcome.Rejected
  }

}
