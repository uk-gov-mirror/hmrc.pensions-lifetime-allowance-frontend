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

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import config.wiring.PlaFormPartialRetriever
import config.{FrontendAppConfig, LocalTemplateRenderer, PlaContext}
import connectors.{IdentityVerificationConnector, KeyStoreConnector}
import enums.IdentityVerificationResult
import org.mockito.ArgumentMatchers
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar
import play.api.Application
import play.api.http.Status
import play.api.libs.json.{Format, Json}
import play.api.mvc.MessagesControllerComponents
import play.api.test.FakeRequest
import play.api.test.Helpers._
import testHelpers._
import uk.gov.hmrc.http.cache.client.CacheMap
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.bootstrap.http.DefaultHttpClient
import utils.ActionWithSessionId

import scala.concurrent.Future
import scala.io.Source

class UnauthorisedControllerSpec extends FakeApplication with MockitoSugar with BeforeAndAfterEach {

  val mockMCC: MessagesControllerComponents = fakeApplication.injector.instanceOf[MessagesControllerComponents]
  val mockActionWithSessionId: ActionWithSessionId = mock[ActionWithSessionId]
  val mockHttp: DefaultHttpClient = mock[DefaultHttpClient]
  val fakeRequest = FakeRequest("GET", "/")
  val mockKeystoreConnector: KeyStoreConnector = mock[KeyStoreConnector]
  val mockAppConfig: FrontendAppConfig = fakeApplication.injector.instanceOf[FrontendAppConfig]
  val mockIdentityVerificationConnector: IdentityVerificationConnector = mock[IdentityVerificationConnector]

  implicit val mockImplAppConfig: FrontendAppConfig = fakeApplication.injector.instanceOf[FrontendAppConfig]
  implicit val mockPlaContext: PlaContext = mock[PlaContext]
  implicit val system: ActorSystem = ActorSystem()
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val hc: HeaderCarrier = mock[HeaderCarrier]
  implicit val templateRenderer: LocalTemplateRenderer = MockTemplateRenderer.renderer
  implicit val partialRetriever: PlaFormPartialRetriever = mock[PlaFormPartialRetriever]
  implicit val application = mock[Application]

  object MockIdentityVerificationHttp extends MockitoSugar {
    val possibleJournies = Map(
      "success-journey-id" -> "test/resources/identity-verification/success.json",
      "incomplete-journey-id" -> "test/resources/identity-verification/incomplete.json",
      "failed-matching-journey-id" -> "test/resources/identity-verification/failed-matching.json",
      "insufficient-evidence-journey-id" -> "test/resources/identity-verification/insufficient-evidence.json",
      "locked-out-journey-id" -> "test/resources/identity-verification/locked-out.json",
      "user-aborted-journey-id" -> "test/resources/identity-verification/user-aborted.json",
      "timeout-journey-id" -> "test/resources/identity-verification/timeout.json",
      "technical-issue-journey-id" -> "test/resources/identity-verification/technical-issue.json",
      "precondition-failed-journey-id" -> "test/resources/identity-verification/precondition-failed.json",
      "failed-iv-journey-id" -> "test/resources/identity-verification/failed-iv.json",
      "invalid-journey-id" -> "test/resources/identity-verification/invalid-result.json",
      "invalid-fields-journey-id" -> "test/resources/identity-verification/invalid-fields.json"
    )

    def mockJourneyId(journeyId: String): Unit = {
      val fileContents = Source.fromFile(possibleJournies(journeyId)).mkString
      when(mockHttp.GET[HttpResponse](ArgumentMatchers.contains(journeyId))(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())).
        thenReturn(Future.successful(HttpResponse(status = Status.OK, json = Json.parse(fileContents), headers = Map.empty)))
    }

    possibleJournies.keys.foreach(mockJourneyId)
  }

  class Setup {
    val controller = new UnauthorisedController(
      mockIdentityVerificationConnector,
      mockKeystoreConnector,
      mockMCC)
  }

  override def beforeEach(): Unit = {
    reset(
      mockKeystoreConnector,
      mockIdentityVerificationConnector
    )
    super.beforeEach()
  }

  def setupKeystoreMocks(data: Option[Boolean]): Unit = {
    when(mockKeystoreConnector
      .fetchAndGetFormData[Boolean](ArgumentMatchers.eq("previous-technical-issues"))(ArgumentMatchers.any[HeaderCarrier], ArgumentMatchers.any[Format[Boolean]]))
      .thenReturn(Future.successful(data))
    when(mockKeystoreConnector
      .saveFormData(ArgumentMatchers.eq("previous-technical-issues"), ArgumentMatchers.any[Boolean])(ArgumentMatchers.any[HeaderCarrier], ArgumentMatchers.any[Format[Boolean]]))
      .thenReturn(Future.successful(mock[CacheMap]))
  }

    "GET /not-authorised" should {
      "show not authorised page" in new Setup {
        val result = controller.showNotAuthorised(None)(fakeRequest)
        contentAsString(result) should include("We cannot confirm your identity")
        status(result) shouldBe UNAUTHORIZED
      }

      "show generic not_authorised template for FailedMatching journey" in new Setup {
        when(mockIdentityVerificationConnector.identityVerificationResponse(ArgumentMatchers.any())(ArgumentMatchers.any[HeaderCarrier]))
          .thenReturn(Future.successful(IdentityVerificationResult.FailedMatching))
        val result = controller.showNotAuthorised(Some("failed-matching-journey-id"))(fakeRequest)
        contentAsString(result) should include("We cannot confirm your identity")
        status(result) shouldBe UNAUTHORIZED
      }

      "show generic not_authorised template for InsufficientEvidence journey" in new Setup {
        when(mockIdentityVerificationConnector.identityVerificationResponse(ArgumentMatchers.any())(ArgumentMatchers.any[HeaderCarrier]))
          .thenReturn(Future.successful(IdentityVerificationResult.InsufficientEvidence))
        val result = controller.showNotAuthorised(Some("insufficient-evidence-journey-id"))(fakeRequest)
        contentAsString(result) should include("We cannot confirm your identity")
        status(result) shouldBe UNAUTHORIZED
      }

      "show generic not_authorised template for Incomplete journey" in new Setup {
        when(mockIdentityVerificationConnector.identityVerificationResponse(ArgumentMatchers.any())(ArgumentMatchers.any[HeaderCarrier]))
          .thenReturn(Future.successful(IdentityVerificationResult.Incomplete))
        val result = controller.showNotAuthorised(Some("incomplete-journey-id"))(fakeRequest)
        contentAsString(result) should include("We cannot confirm your identity")
        status(result) shouldBe UNAUTHORIZED
      }

      "show generic not_authorised template for PreconditionFailed journey" in new Setup {
        when(mockIdentityVerificationConnector.identityVerificationResponse(ArgumentMatchers.any())(ArgumentMatchers.any[HeaderCarrier]))
          .thenReturn(Future.successful(IdentityVerificationResult.PreconditionFailed))
        val result = controller.showNotAuthorised(Some("precondition-failed-journey-id"))(fakeRequest)
        contentAsString(result) should include("We cannot confirm your identity")
        status(result) shouldBe UNAUTHORIZED
      }

      "show generic not_authorised template for UserAborted journey" in new Setup {
        when(mockIdentityVerificationConnector.identityVerificationResponse(ArgumentMatchers.any())(ArgumentMatchers.any[HeaderCarrier]))
          .thenReturn(Future.successful(IdentityVerificationResult.UserAborted))
        val result = controller.showNotAuthorised(Some("user-aborted-journey-id"))(fakeRequest)
        contentAsString(result) should include("We cannot confirm your identity")
        status(result) shouldBe UNAUTHORIZED
      }
    }

  "show technical_issue template for TechnicalIssue journey" which {

    "returns an INTERNAL_SERVER_ERROR on the first attempt" in new Setup {
      when(mockIdentityVerificationConnector.identityVerificationResponse(ArgumentMatchers.any())(ArgumentMatchers.any()))
        .thenReturn(Future.successful(IdentityVerificationResult.TechnicalIssue))

      setupKeystoreMocks(None)

      val result = controller.showNotAuthorised(Some("technical-issue-journey-id"))(fakeRequest)
      contentAsString(result) should include("There is a technical problem")
      status(result) shouldBe INTERNAL_SERVER_ERROR
    }

    "returns an OK on any attempt after the first" in new Setup {
      when(mockIdentityVerificationConnector.identityVerificationResponse(ArgumentMatchers.any())(ArgumentMatchers.any()))
        .thenReturn(Future.successful(IdentityVerificationResult.TechnicalIssue))

      setupKeystoreMocks(Some(true))
      val result = controller.showNotAuthorised(Some("technical-issue-journey-id"))(fakeRequest)

      contentAsString(result) should include("There is a technical problem")
      status(result) shouldBe OK
    }

     "returns an INTERNAL_SERVER_ERROR if a false is returned" in new Setup {
       when(mockIdentityVerificationConnector.identityVerificationResponse(ArgumentMatchers.any())(ArgumentMatchers.any()))
         .thenReturn(Future.successful(IdentityVerificationResult.TechnicalIssue))
       setupKeystoreMocks(Some(false))
       val result = controller.showNotAuthorised(Some("technical-issue-journey-id"))(fakeRequest)
       contentAsString(result) should include("There is a technical problem")
       status(result) shouldBe INTERNAL_SERVER_ERROR
     }
   }

     "show locked_out template for LockedOut journey" in new Setup {
       when(mockIdentityVerificationConnector.identityVerificationResponse(ArgumentMatchers.any())(ArgumentMatchers.any()))
         .thenReturn(Future.successful(IdentityVerificationResult.LockedOut))

       val result = controller.showNotAuthorised(Some("locked-out-journey-id"))(fakeRequest)
       contentAsString(result) should include("You have tried to confirm your identity too many times")
       status(result) shouldBe UNAUTHORIZED
     }

      "show timeout template for Timeout journey" in new Setup {
        when(mockIdentityVerificationConnector.identityVerificationResponse(ArgumentMatchers.any())(ArgumentMatchers.any()))
          .thenReturn(Future.successful(IdentityVerificationResult.Timeout))

        val result = controller.showNotAuthorised(Some("timeout-journey-id"))(fakeRequest)
        contentAsString(result) should include("signed out due to inactivity")
        status(result) shouldBe UNAUTHORIZED
     }

      "show 2FA failure page when no journey ID specified" in new Setup {
        val result = controller.showNotAuthorised(None)(fakeRequest)
        contentAsString(result) should include("We cannot confirm your identity")
        contentAsString(result) should not include "If you cannot confirm your identity and you have a query you can"
        status(result) shouldBe UNAUTHORIZED
      }
  }
