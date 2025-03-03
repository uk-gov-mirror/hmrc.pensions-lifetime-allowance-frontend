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

import java.time.LocalDateTime

import auth.AuthFunction
import common.{Dates, Strings}
import config._
import config.wiring.PlaFormPartialRetriever
import connectors.{KeyStoreConnector, PLAConnector}
import constructors.DisplayConstructors
import enums.ApplicationType
import forms.WithdrawDateForm._
import javax.inject.Inject
import models.{ProtectionModel, WithdrawDateFormModel}
import play.api.data.{Form, FormError}
import play.api.i18n.{I18nSupport, Lang, Messages}
import play.api.mvc._
import play.api.Application
import play.api.Logger.logger
import uk.gov.hmrc.http.HttpResponse
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class WithdrawProtectionController @Inject()(keyStoreConnector: KeyStoreConnector,
                                             plaConnector: PLAConnector,
                                             displayConstructors: DisplayConstructors,
                                             mcc: MessagesControllerComponents,
                                             authFunction: AuthFunction)
                                            (implicit val appConfig: FrontendAppConfig,
                                             implicit val partialRetriever: PlaFormPartialRetriever,
                                             implicit val templateRenderer:LocalTemplateRenderer,
                                             implicit val plaContext: PlaContext,
                                             implicit val application: Application)
extends FrontendController(mcc) with I18nSupport {


  lazy val postSignInRedirectUrl = appConfig.existingProtectionsUrl

  /** Withdraw protections journey **/
  def withdrawSummary: Action[AnyContent] = Action.async {
    implicit request =>
      authFunction.genericAuthWithNino("existingProtections") { nino =>
        keyStoreConnector.fetchAndGetFormData[ProtectionModel]("openProtection") map {
          case Some(currentProtection) =>
            Ok(views.html.pages.withdraw.withdrawSummary(displayConstructors.createWithdrawSummaryTable(currentProtection)))
          case _ =>
            logger.error(s"Could not retrieve protection data for user with nino $nino when loading the withdraw summary page")
            InternalServerError(views.html.pages.fallback.technicalError(ApplicationType.existingProtections.toString)).withHeaders(CACHE_CONTROL -> "no-cache")
        }
      }
  }

  def withdrawImplications: Action[AnyContent] = Action.async {
    implicit request =>
      authFunction.genericAuthWithNino("existingProtections") { nino =>
        keyStoreConnector.fetchAndGetFormData[ProtectionModel]("openProtection") map {
          case Some(currentProtection) =>
            Ok(views.html.pages.withdraw.withdrawImplications(withdrawDateForm,
              Strings.protectionTypeString(currentProtection.protectionType),
              Strings.statusString(currentProtection.status)))
          case _ =>
            logger.error(s"Could not retrieve protection data for user with nino $nino when loading the withdraw summary page")
            InternalServerError(views.html.pages.fallback.technicalError(ApplicationType.existingProtections.toString)).withHeaders(CACHE_CONTROL -> "no-cache")
        }
      }
  }

  def getWithdrawDateInput: Action[AnyContent] = Action.async {
    implicit request =>
      authFunction.genericAuthWithNino("existingProtections") { nino =>
        keyStoreConnector.fetchAndGetFormData[ProtectionModel]("openProtection") map {
          case Some(currentProtection) =>
            Ok(views.html.pages.withdraw.withdrawDate(withdrawDateForm,
              Strings.protectionTypeString(currentProtection.protectionType),
              Strings.statusString(currentProtection.status)))
          case _ =>
            logger.error(s"Could not retrieve protection data for user with nino $nino when loading the withdraw summary page")
            InternalServerError(views.html.pages.fallback.technicalError(ApplicationType.existingProtections.toString)).withHeaders(CACHE_CONTROL -> "no-cache")
        }
      }
  }

  private[controllers] def validateAndSaveWithdrawDateForm(protection: ProtectionModel)(implicit request: Request[_]) = {
    validateWithdrawDate(
      withdrawDateForm.bindFromRequest(),
      LocalDateTime.parse(protection.certificateDate.get)
    ).fold(
      formWithErrors =>
        Future.successful(
          BadRequest(
            views.html.pages.withdraw.withdrawDate(buildInvalidForm(formWithErrors),
              Strings.protectionTypeString(protection.protectionType),
              Strings.statusString(protection.status))
          )
        ),
      success =>
        keyStoreConnector.saveFormData[WithdrawDateFormModel]("withdrawProtectionForm", success) map {
          _  =>
            Redirect(routes.WithdrawProtectionController.getSubmitWithdrawDateInput())
        }
    )
  }

  def postWithdrawDateInput: Action[AnyContent] = Action.async { implicit request =>
    authFunction.genericAuthWithoutNino("existingProtections") {
      keyStoreConnector.fetchAndGetFormData[ProtectionModel]("openProtection") flatMap {
        case Some(protection) => validateAndSaveWithdrawDateForm(protection)
        case _ =>
          logger.error(s"Could not retrieve protection data for user when loading the withdraw date input page")
          Future.successful(InternalServerError(views.html.pages.fallback.technicalError(ApplicationType.existingProtections.toString)).withHeaders(CACHE_CONTROL -> "no-cache"))
      }
    }
  }


  private[controllers] def fetchWithdrawDateForm(protection: ProtectionModel)(implicit request: Request[_], lang: Lang) = {
    keyStoreConnector.fetchAndGetFormData[WithdrawDateFormModel]("withdrawProtectionForm") map  {
      case Some(form) =>
        Ok(views.html.pages.withdraw.withdrawConfirm(
          getWithdrawDateModel(form), Strings.protectionTypeString(protection.protectionType),
          Strings.statusString(protection.status)))
      case _ =>
        logger.error(s"Could not retrieve withdraw form data for user")
        InternalServerError(views.html.pages.fallback.technicalError(ApplicationType.existingProtections.toString)).withHeaders(CACHE_CONTROL -> "no-cache")
    }
  }

  def getSubmitWithdrawDateInput: Action[AnyContent] = Action.async {
    implicit request =>
      implicit val lang = mcc.messagesApi.preferred(request).lang
      authFunction.genericAuthWithNino("existingProtections") { nino =>
        keyStoreConnector.fetchAndGetFormData[ProtectionModel]("openProtection") flatMap  {
          case Some(protection) =>
            fetchWithdrawDateForm(protection)
          case _ =>
            logger.error(s"Could not retrieve protection data for user with nino $nino when loading the withdraw date input page")
            Future.successful(InternalServerError(views.html.pages.fallback.technicalError(ApplicationType.existingProtections.toString)).withHeaders(CACHE_CONTROL -> "no-cache"))
        }
      }
  }

  def submitWithdrawDateInput: Action[AnyContent] = Action.async {
    implicit request =>
      implicit val lang = mcc.messagesApi.preferred(request).lang
      authFunction.genericAuthWithNino("existingProtections") { nino =>
        keyStoreConnector.fetchAndGetFormData[ProtectionModel]("openProtection") map {
          case Some(protection) =>
            validateWithdrawDate(withdrawDateForm.bindFromRequest(),
            LocalDateTime.parse(protection.certificateDate.get)).fold(
            formWithErrors =>
                BadRequest(views.html.pages.withdraw.withdrawDate(buildInvalidForm(formWithErrors),
              Strings.protectionTypeString(protection.protectionType),
              Strings.statusString(protection.status))),
            _ => Ok(views.html.pages.withdraw.withdrawConfirm(
              getWithdrawDate(withdrawDateForm.bindFromRequest()), Strings.protectionTypeString(protection.protectionType),
              Strings.statusString(protection.status)))
          )
          case _ =>
            logger.error(s"Could not retrieve protection data for user with nino $nino when loading the withdraw date input page")
            InternalServerError(views.html.pages.fallback.technicalError(ApplicationType.existingProtections.toString)).withHeaders(CACHE_CONTROL -> "no-cache")
        }
      }
  }


  def displayWithdrawConfirmation(withdrawDate: String): Action[AnyContent] = Action.async {
    implicit request =>
      authFunction.genericAuthWithNino("existingProtections") { nino =>
        for {
          protectionAmendment <- keyStoreConnector.fetchAndGetFormData[ProtectionModel]("openProtection")
          response <- plaConnector.amendProtection(nino, protectionAmendment.get.copy(withdrawnDate = Some(withdrawDate), status = Some("Withdrawn")))
          result <- routeToWithdrawConfirmation(protectionAmendment.get.protectionType, response, nino)
        } yield result
      }
  }

  private def routeToWithdrawConfirmation(protectionType: Option[String], response: HttpResponse, nino: String)
                                         (implicit request: Request[AnyContent]): Future[Result] = {
    response.status match {
      case OK => Future.successful(Redirect(routes.WithdrawProtectionController.showWithdrawConfirmation(Strings.protectionTypeString(protectionType))))
      case _ => {
        logger.error(s"conflict response returned for withdrawal request for user nino $nino")
        Future.successful(InternalServerError(
          views.html.pages.fallback.technicalError(ApplicationType.existingProtections.toString))
          .withHeaders(CACHE_CONTROL -> "no-cache"))
      }
    }
  }


  def showWithdrawConfirmation(protectionType: String): Action[AnyContent] = Action.async {
    implicit request =>
      authFunction.genericAuthWithoutNino("existingProtections") {
        keyStoreConnector.remove.map {
          _ => Ok(views.html.pages.withdraw.withdrawConfirmation(protectionType))
        }
      }
  }


  private def buildInvalidForm(errorForm: Form[WithdrawDateFormModel])(implicit messages: Messages): Form[WithdrawDateFormModel] = {
    if (errorForm.errors.size > 1) {
      val formFields: Seq[String] = errorForm.errors map (field => field.key)
      val formFieldsWithErrors: Seq[FormError] = formFields map (key => FormError(key, ""))
      val finalErrors: Seq[FormError] = formFieldsWithErrors ++ Seq(FormError("", Messages("pla.withdraw.date-input-form.date-invalid")))
      withdrawDateForm.copy(errors = finalErrors, data = errorForm.data)
    }
    else errorForm
  }

  def getWithdrawDate(form: Form[WithdrawDateFormModel]) : String = {
    Dates.apiDateFormat(form.get.withdrawDay.get,form.get.withdrawMonth.get,form.get.withdrawYear.get)
  }

  def getWithdrawDateModel(form: WithdrawDateFormModel) : String = {
    Dates.apiDateFormat(form.withdrawDay.get,form.withdrawMonth.get,form.withdrawYear.get)
  }
}
