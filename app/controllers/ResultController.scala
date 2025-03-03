/*
 * Copyright 2021 HM Revenue & Customs
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

import auth.AuthFunction
import common.Exceptions
import config._
import config.wiring.PlaFormPartialRetriever
import connectors.{KeyStoreConnector, PLAConnector}
import constructors.{DisplayConstructors, ResponseConstructors}
import enums.{ApplicationOutcome, ApplicationType}
import javax.inject.Inject
import models._
import play.api.i18n.I18nSupport
import play.api.mvc._
import play.api.Application
import play.api.Logger.logger
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import utils.Constants
import views.html.pages.result._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Random

class ResultController @Inject()(keyStoreConnector: KeyStoreConnector,
                                 plaConnector: PLAConnector,
                                 displayConstructors: DisplayConstructors,
                                 mcc: MessagesControllerComponents,
                                 responseConstructors: ResponseConstructors,
                                 authFunction: AuthFunction)
                                (implicit val appConfig: FrontendAppConfig,
                                 implicit val partialRetriever: PlaFormPartialRetriever,
                                 implicit val templateRenderer:LocalTemplateRenderer,
                                 implicit val plaContext: PlaContext,
                                 implicit val application: Application)
extends FrontendController(mcc) with I18nSupport {

  lazy val postSignInRedirectUrl = appConfig.existingProtectionsUrl

  val processFPApplication = Action.async { implicit request =>
    authFunction.genericAuthWithNino("FP2016") { nino =>
      implicit val protectionType = ApplicationType.FP2016
      plaConnector.applyFP16(nino).flatMap(
        response => routeViaMCNeededCheck(response, nino)
      )
    }
  }

  val processIPApplication = Action.async {
    implicit request =>
      authFunction.genericAuthWithNino("IP2016") { nino =>
        implicit val protectionType = ApplicationType.IP2016
        for {
          userData <- keyStoreConnector.fetchAllUserData
          applicationResult <- plaConnector.applyIP16(nino, userData.get)
          response <- routeViaMCNeededCheck(applicationResult, nino)
        } yield response
      }
  }


  private[controllers] def routeViaMCNeededCheck(response: HttpResponse, nino: String)(implicit request: Request[AnyContent], protectionType: ApplicationType.Value): Future[Result] = {
    response.status match {
      case 423 => Future.successful(Locked(manualCorrespondenceNeeded()))
      case _ => saveAndRedirectToDisplay(response, nino)
    }
  }


  private[controllers] def saveAndRedirectToDisplay(response: HttpResponse, nino: String)(implicit request: Request[AnyContent], protectionType: ApplicationType.Value): Future[Result] = {
    responseConstructors.createApplyResponseModelFromJson(response.json).map {
      model =>
        if (model.protection.notificationId.isEmpty) {
          logger.warn(s"No notification ID found in the ApplyResponseModel for user with nino $nino")
          Future.successful(InternalServerError(views.html.pages.fallback.noNotificationId()).withHeaders(CACHE_CONTROL -> "no-cache"))
        } else {
          keyStoreConnector.saveData[ApplyResponseModel](common.Strings.nameString("applyResponseModel"), model).map {
            cacheMap =>
              protectionType match {
                case ApplicationType.IP2016 => Redirect(routes.ResultController.displayIP16())
                case ApplicationType.FP2016 => Redirect(routes.ResultController.displayFP16())
              }
          }
        }
    }.getOrElse {
      logger.warn(s"Unable to create ApplyResponseModel from application response for ${protectionType.toString} for user nino: $nino")
      Future.successful(InternalServerError(views.html.pages.fallback.technicalError(protectionType.toString)).withHeaders(CACHE_CONTROL -> "no-cache"))
    }
  }


  val displayIP16 = displayResult(ApplicationType.IP2016)
  val displayFP16 = displayResult(ApplicationType.FP2016)


  def displayResult(implicit protectionType: ApplicationType.Value): Action[AnyContent] = Action.async {
    implicit request =>
      implicit val lang = mcc.messagesApi.preferred(request).lang
      authFunction.genericAuthWithNino("existingProtections") { nino =>
        val showUserResearchPanel = setURPanelFlag
        val errorResponse = InternalServerError(views.html.pages.fallback.technicalError(protectionType.toString)).withHeaders(CACHE_CONTROL -> "no-cache")
        keyStoreConnector.fetchAndGetFormData[ApplyResponseModel](common.Strings.nameString("applyResponseModel")).map {
          case Some(model) =>
            val notificationId = model.protection.notificationId.getOrElse {
              throw new Exceptions.OptionNotDefinedException("applicationOutcome", "notificationId", protectionType.toString)
            }
            applicationOutcome(notificationId) match {

              case ApplicationOutcome.Successful =>
                keyStoreConnector.saveData[ProtectionModel]("openProtection", model.protection)
                val displayModel = displayConstructors.createSuccessDisplayModel(model)
                Ok(resultSuccess(displayModel, showUserResearchPanel))

              case ApplicationOutcome.SuccessfulInactive =>
                val displayModel = displayConstructors.createSuccessDisplayModel(model)
                Ok(resultSuccessInactive(displayModel, showUserResearchPanel))

              case ApplicationOutcome.Rejected =>
                val displayModel = displayConstructors.createRejectionDisplayModel(model)
                Ok(resultRejected(displayModel, showUserResearchPanel))
            }
          case _ =>
            logger.warn(s"Could not retrieve ApplyResponseModel from keystore for user with nino: $nino")
            errorResponse
        }
      }
  }

  private[controllers] def setURPanelFlag(implicit hc: HeaderCarrier): Boolean = {
    val random = new Random()
    val seed = getLongFromSessionID(hc)
    random.setSeed(seed)
    random.nextInt(3) == 0
  }

  private[controllers] def getLongFromSessionID(implicit hc: HeaderCarrier): Long = {
    val session = hc.sessionId.map(_.value).getOrElse("0")
    val numericSessionValues = session.replaceAll("[^0-9]", "") match {
      case "" => "0"
      case num => num
    }
    numericSessionValues.takeRight(10).toLong
  }


  def applicationOutcome(notificationId: Int)(implicit protectionType: ApplicationType.Value): ApplicationOutcome.Value = {
    val successCodes = protectionType match {
      case ApplicationType.FP2016 => Constants.successCodes
      case ApplicationType.IP2016 => Constants.ip16SuccessCodes
      case ApplicationType.IP2014 => Constants.ip14SuccessCodes
    }
    if (successCodes.contains(notificationId)) successfulOutcomeType(notificationId) else ApplicationOutcome.Rejected
  }

  private def successfulOutcomeType(notificationId: Int): ApplicationOutcome.Value = {
    if(Constants.inactiveSuccessCodes.contains(notificationId)) ApplicationOutcome.SuccessfulInactive else ApplicationOutcome.Successful
  }

}
