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
import auth.AuthFunction
import config.wiring.PlaFormPartialRetriever
import config.{FrontendAppConfig, LocalTemplateRenderer, PlaContext}
import connectors.{KeyStoreConnector, PLAConnector}
import mocks.AuthMock
import models._
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar
import play.api.i18n.Messages
import play.api.libs.json.JsValue
import play.api.mvc.MessagesControllerComponents
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.api.{Application, Configuration, Environment}
import testHelpers._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.cache.client.CacheMap

import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class IP2016ControllerSpec extends FakeApplication with MockitoSugar
  with BeforeAndAfterEach with KeystoreTestHelper with AuthMock {


    val mockKeyStoreConnector: KeyStoreConnector = mock[KeyStoreConnector]
    val mockPlaConnector: PLAConnector = mock[PLAConnector]
    val mockMCC: MessagesControllerComponents = fakeApplication.injector.instanceOf[MessagesControllerComponents]
    val mockAuthFunction: AuthFunction = fakeApplication.injector.instanceOf[AuthFunction]
    val mockEnv: Environment = mock[Environment]

    implicit val mockTemplateRenderer: LocalTemplateRenderer = MockTemplateRenderer.renderer
    implicit val mockPartialRetriever: PlaFormPartialRetriever = mock[PlaFormPartialRetriever]
    implicit val mockAppConfig: FrontendAppConfig = fakeApplication.injector.instanceOf[FrontendAppConfig]
    implicit val mockPlaContext: PlaContext = mock[PlaContext]
    implicit val mockMessages: Messages = mock[Messages]
    implicit val system: ActorSystem = ActorSystem()
    implicit val materializer: ActorMaterializer = ActorMaterializer()
    implicit val application = mock[Application]

    class Setup {

        val authFunction = new AuthFunction {
            override implicit val partialRetriever: PlaFormPartialRetriever = mockPartialRetriever
            override implicit val templateRenderer: LocalTemplateRenderer = mockTemplateRenderer
            override implicit val plaContext: PlaContext = mockPlaContext
            override implicit val appConfig: FrontendAppConfig = mockAppConfig
            override def authConnector: AuthConnector = mockAuthConnector
            override def config: Configuration = mockAppConfig.configuration
            override def env: Environment = mockEnv
        }

        val controller = new IP2016Controller(
            mockKeyStoreConnector,
            mockMCC,
            authFunction
        )
    }

    override def beforeEach = {
        reset(mockKeyStoreConnector, mockAuthConnector, mockPlaConnector)
    }

    implicit val hc = HeaderCarrier()

    //    lazy val TestIP2016Controller = fakeApplication.injector.instanceOf[IP2016Controller]
    object TestIP2016Controller extends IP2016Controller(mockKeyStoreConnector, mockMCC, mockAuthFunction) {
        lazy val authConnector = mockAuthConnector
    }

    val sessionId = UUID.randomUUID.toString
    val fakeRequest = FakeRequest()

    val mockUsername = "mockuser"
    val mockUserId = "/auth/oid/" + mockUsername

    def keystoreFetchCondition[T](data: Option[T]): Unit = {
        when(mockKeyStoreConnector.fetchAndGetFormData[T](ArgumentMatchers.anyString())(ArgumentMatchers.any(), ArgumentMatchers.any()))
          .thenReturn(Future.successful(data))
    }


    def psoDetailsKeystoreSetup(data: Option[PSODetailsModel]) = {
        when(mockKeyStoreConnector.fetchAndGetFormData[PSODetailsModel](ArgumentMatchers.eq(s"psoDetails"))(ArgumentMatchers.any(), ArgumentMatchers.any()))
          .thenReturn(Future.successful(data))
    }

    def pensionsDebitsSaveData(data: Option[PensionDebitsModel]) = {
        when(mockKeyStoreConnector.saveData(anyString(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
          .thenReturn(Future(CacheMap("tstId", Map.empty[String, JsValue])))
    }

    ///////////////////////////////////////////////
    // Pensions Taken
    ///////////////////////////////////////////////
    "In IP2016Controller calling the .pensionsTaken action" when {

        "not supplied with a stored model" should {
            "return 200" in new Setup {
                lazy val result = controller.pensionsTaken(fakeRequest)
                mockAuthConnector(Future.successful({}))


                keystoreFetchCondition[PensionsTakenModel](None)
                status(result) shouldBe 200
            }
        }

        "supplied with a stored test model" should {
            "return 200" in new Setup {
                mockAuthConnector(Future.successful({}))
                val testModel = new PensionsTakenModel(Some("yes"))
                lazy val result = controller.pensionsTaken(fakeRequest)

                mockAuthConnector(Future.successful({}))
                keystoreFetchCondition[PensionsTakenModel](Some(testModel))
                status(result) shouldBe 200
            }

            "return some HTML that" should {
                "contain some text and use the character set utf-8" in new Setup {
                    mockAuthConnector(Future.successful({}))
                    val testModel = new PensionsTakenModel(Some("yes"))
                    lazy val result = controller.pensionsTaken(fakeRequest)

                    keystoreFetchCondition[PensionsTakenModel](Some(testModel))
                    contentType(result) shouldBe Some("text/html")
                    charset(result) shouldBe Some("utf-8")
                }
            }
        }
    }

    "Submitting Pensions Taken data" when {

        "Submitting 'yes' in pensionsTakenForm" should {
            "redirect to pensions taken before" in new Setup {

                object DataItem extends AuthorisedFakeRequestToPost(controller.submitPensionsTaken, ("pensionsTaken", "yes"))

                mockAuthConnector(Future.successful({}))
                keystoreSaveCondition[PensionsTakenModel](mockKeyStoreConnector)
                status(DataItem.result) shouldBe 303
                redirectLocation(DataItem.result) shouldBe Some(s"${routes.IP2016Controller.pensionsTakenBefore()}")
            }
        }

        "Submitting 'no' in pensionsTakenForm" should {
            "redirect to overseas pensions" in new Setup {

                object DataItem extends AuthorisedFakeRequestToPost(controller.submitPensionsTaken, ("pensionsTaken", "no"))

                mockAuthConnector(Future.successful({}))
                keystoreSaveCondition[PensionsTakenModel](mockKeyStoreConnector)
                status(DataItem.result) shouldBe 303
                redirectLocation(DataItem.result) shouldBe Some(s"${routes.IP2016Controller.overseasPensions()}")
            }
        }

        "Submitting pensionsTakenForm with no data" should {
            "return 400" in new Setup {

                object DataItem extends AuthorisedFakeRequestToPost(controller.submitPensionsTaken, ("pensionsTaken", ""))

                mockAuthConnector(Future.successful({}))
                status(DataItem.result) shouldBe 400
            }
        }
    }


    ///////////////////////////////////////////////
    // Pensions Taken Before
    ///////////////////////////////////////////////
    "In IP2016Controller calling the .pensionsTakenBefore action" when {

        "not supplied with a stored model" should {
            "return 200" in new Setup {
                lazy val result = controller.pensionsTakenBefore(fakeRequest)
                mockAuthConnector(Future.successful({}))

                keystoreFetchCondition[PensionsTakenBeforeModel](None)
                status(result) shouldBe 200
            }

            "take the user to the pensions taken before page" in {
                mockAuthConnector(Future.successful({}))
                keystoreFetchCondition[PensionsTakenBeforeModel](None)
            }
        }

        "supplied with a stored test model" should {
            "return 200" in new Setup {
                val testModel = new PensionsTakenBeforeModel("yes", Some(1))
                lazy val result = controller.pensionsTakenBefore(fakeRequest)

                mockAuthConnector(Future.successful({}))
                keystoreFetchCondition[PensionsTakenBeforeModel](Some(testModel))
                status(result) shouldBe 200
            }

            "take the user to the pensions taken before page" in {
                mockAuthConnector(Future.successful({}))
                keystoreFetchCondition[PensionsTakenBeforeModel](None)
            }

            "return some HTML that" should {

                "contain some text and use the character set utf-8" in new Setup {
                    val testModel = new PensionsTakenBeforeModel("yes", Some(1))
                    lazy val result = controller.pensionsTakenBefore(fakeRequest)

                    mockAuthConnector(Future.successful({}))
                    keystoreFetchCondition[PensionsTakenBeforeModel](Some(testModel))
                    contentType(result) shouldBe Some("text/html")
                    charset(result) shouldBe Some("utf-8")
                }

                "have the radio option `yes` selected by default" in new Setup {
                    val testModel = new PensionsTakenBeforeModel("yes", Some(1))
                    mockAuthConnector(Future.successful({}))
                    keystoreFetchCondition[PensionsTakenBeforeModel](Some(testModel))
                }

                "have the amount £1 completed by default" in new Setup {
                    val testModel = new PensionsTakenBeforeModel("yes", Some(1))
                    mockAuthConnector(Future.successful({}))
                    keystoreFetchCondition[PensionsTakenBeforeModel](Some(testModel))
                }
            }
        }
    }

    "Submitting Pensions Taken Before data" when {

        "Submitting 'yes' in pensionsTakenBeforeForm" when {

            "valid data is submitted" should {
                "redirect to pensions taken between" in new Setup {

                    object DataItem extends AuthorisedFakeRequestToPost(controller.submitPensionsTakenBefore, ("pensionsTakenBefore", "yes"), ("pensionsTakenBeforeAmt", "1"))

                    mockAuthConnector(Future.successful({}))

                    keystoreSaveCondition[PensionsTakenModel](mockKeyStoreConnector)
                    status(DataItem.result) shouldBe 303
                    redirectLocation(DataItem.result) shouldBe Some(s"${routes.IP2016Controller.pensionsTakenBetween()}")
                }
            }

            "invalid data is submitted" should {
                "return 400" in new Setup {

                    object DataItem extends AuthorisedFakeRequestToPost(controller.submitPensionsTakenBefore, ("pensionsTakenBefore", ""), ("pensionsTakenBeforeAmt", ""))

                    mockAuthConnector(Future.successful({}))

                    status(DataItem.result) shouldBe 400
                }
            }

            "invalid data is submitted that fails additional validation" should {
                "return 400" in new Setup {

                    object DataItem extends AuthorisedFakeRequestToPost(controller.submitPensionsTakenBefore, ("pensionsTakenBefore", "yes"), ("pensionsTakenBeforeAmt", ""))

                    mockAuthConnector(Future.successful({}))
                    status(DataItem.result) shouldBe 400
                }
            }
        }
    }

    ///////////////////////////////////////////////
    // Pensions Taken Between
    ///////////////////////////////////////////////
    "In IP2016Controller calling the .pensionsTakenBetween action" when {

        "not supplied with a stored model" should {
            "return 200" in new Setup {
                lazy val result = controller.pensionsTakenBetween(fakeRequest)
                mockAuthConnector(Future.successful({}))
                keystoreFetchCondition[PensionsTakenBetweenModel](None)
                status(result) shouldBe 200
            }
        }

        "supplied with a stored test model" should {
            "return 200" in new Setup {
                val testModel = new PensionsTakenBetweenModel("yes", Some(1))
                lazy val result = controller.pensionsTakenBetween(fakeRequest)

                mockAuthConnector(Future.successful({}))
                keystoreFetchCondition[PensionsTakenBetweenModel](Some(testModel))
                status(result) shouldBe 200
            }

            "return some HTML that" should {

                "contain some text and use the character set utf-8" in new Setup {
                    val testModel = new PensionsTakenBetweenModel("yes", Some(1))
                    lazy val result = controller.pensionsTakenBetween(fakeRequest)

                    mockAuthConnector(Future.successful({}))
                    keystoreFetchCondition[PensionsTakenBetweenModel](Some(testModel))
                    contentType(result) shouldBe Some("text/html")
                    charset(result) shouldBe Some("utf-8")
                }
            }
        }
    }

    "Submitting Pensions Taken Between data" when {

        "Submitting 'yes' in pensionsTakenBetweenForm" when {

            "submitting valid data" should {
                "redirect to overseas pensions" in new Setup {

                    object DataItem extends AuthorisedFakeRequestToPost(controller.submitPensionsTakenBetween, ("pensionsTakenBetween", "yes"), ("pensionsTakenBetweenAmt", "1"))

                    mockAuthConnector(Future.successful({}))
                    keystoreSaveCondition[PensionsTakenModel](mockKeyStoreConnector)
                    status(DataItem.result) shouldBe 303
                    redirectLocation(DataItem.result) shouldBe Some(s"${routes.IP2016Controller.overseasPensions}")
                }
            }

            "submitting invalid data" should {
                "return 400" in new Setup {

                    object DataItem extends AuthorisedFakeRequestToPost(controller.submitPensionsTakenBetween, ("pensionsTakenBetween", ""), ("pensionsTakenBetweenAmt", ""))

                    mockAuthConnector(Future.successful({}))
                    status(DataItem.result) shouldBe 400
                }
            }

            "submitting invalid data that fails additional validation" should {
                "return 400" in new Setup {

                    object DataItem extends AuthorisedFakeRequestToPost(controller.submitPensionsTakenBetween, ("pensionsTakenBetween", "yes"), ("pensionsTakenBetweenAmt", ""))

                    mockAuthConnector(Future.successful({}))
                    status(DataItem.result) shouldBe 400
                }
            }
        }
    }


    ///////////////////////////////////////////////
    // Overseas Pensions
    ///////////////////////////////////////////////
    "In IP2016Controller calling the .overseasPensions action" when {

        "not supplied with a stored model" should {
            "return 200" in new Setup {

                lazy val result = controller.overseasPensions(fakeRequest)
                mockAuthConnector(Future.successful({}))
                keystoreFetchCondition[OverseasPensionsModel](None)
                status(result) shouldBe 200
            }
        }

        "supplied with a stored test model (yes, £100000)" should {
            "return 200" in new Setup {
                val testModel = new OverseasPensionsModel("yes", Some(100000))
                lazy val result = controller.overseasPensions(fakeRequest)

                mockAuthConnector(Future.successful({}))
                keystoreFetchCondition[OverseasPensionsModel](Some(testModel))
                status(result) shouldBe 200
            }

            "return some HTML that" should {

                "contain some text and use the character set utf-8" in new Setup {
                    val testModel = new OverseasPensionsModel("yes", Some(100000))
                    lazy val result = controller.overseasPensions(fakeRequest)

                    mockAuthConnector(Future.successful({}))
                    keystoreFetchCondition[OverseasPensionsModel](Some(testModel))
                    contentType(result) shouldBe Some("text/html")
                    charset(result) shouldBe Some("utf-8")
                }
            }
        }
    }

    "Submitting Overseas Pensions data" when {


        "Submitting valid data" should {
            "redirect to Current Pensions" in new Setup {

                object DataItem extends AuthorisedFakeRequestToPost(controller.submitOverseasPensions, ("overseasPensions", "no"), ("overseasPensionsAmt", ""))

                mockAuthConnector(Future.successful({}))
                keystoreSaveCondition[PensionsTakenModel](mockKeyStoreConnector)
                status(DataItem.result) shouldBe 303
                redirectLocation(DataItem.result) shouldBe Some(s"${routes.IP2016Controller.currentPensions()}")
            }
        }
        "Submitting invalid data" should {

            "return 400" in new Setup {

                object DataItem extends AuthorisedFakeRequestToPost(controller.submitOverseasPensions, ("overseasPensions", ""), ("overseasPensionsAmt", ""))

                mockAuthConnector(Future.successful({}))
                status(DataItem.result) shouldBe 400
            }
        }

        "Submitting invalid data that fails additional validation" should {
            "return 400" in new Setup {

                object DataItem extends AuthorisedFakeRequestToPost(controller.submitOverseasPensions, ("overseasPensions", "yes"), ("overseasPensionsAmt", ""))

                mockAuthConnector(Future.successful({}))
                status(DataItem.result) shouldBe 400
            }
        }
    }


    ///////////////////////////////////////////////
    // CURRENT PENSIONS
    ///////////////////////////////////////////////
    "In IP2016Controller calling the .currentPensions action" when {

        "not supplied with a stored model" should {
            "return 200" in new Setup {

                lazy val result = controller.currentPensions(fakeRequest)
                mockAuthConnector(Future.successful({}))
                keystoreFetchCondition[CurrentPensionsModel](None)
                status(result) shouldBe 200
            }
        }

        "supplied with a stored test model (£100000)" should {
            "return 200" in new Setup {
                val testModel = new CurrentPensionsModel(Some(100000))
                lazy val result = controller.currentPensions(fakeRequest)

                mockAuthConnector(Future.successful({}))
                keystoreFetchCondition[CurrentPensionsModel](Some(testModel))
                status(result) shouldBe 200
            }


            "return some HTML that" should {

                "contain some text and use the character set utf-8" in new Setup {
                    val testModel = new CurrentPensionsModel(Some(100000))
                    lazy val result = controller.currentPensions(fakeRequest)

                    mockAuthConnector(Future.successful({}))
                    keystoreFetchCondition[CurrentPensionsModel](Some(testModel))
                    contentType(result) shouldBe Some("text/html")
                    charset(result) shouldBe Some("utf-8")
                }

            }
        }
    }

    "Submitting Current Pensions data" when {

        "valid data is submitted" should {
            "redirect to Pension Debits page" in new Setup {

                object DataItem extends AuthorisedFakeRequestToPost(controller.submitCurrentPensions, ("currentPensionsAmt", "100000"))

                mockAuthConnector(Future.successful({}))
                keystoreSaveCondition[PensionsTakenModel](mockKeyStoreConnector)
                status(DataItem.result) shouldBe 303
                redirectLocation(DataItem.result) shouldBe Some(s"${routes.IP2016Controller.pensionDebits()}")
            }
        }

        "invalid data is submitted" should {
            "return 400" in new Setup {

                object DataItem extends AuthorisedFakeRequestToPost(controller.submitCurrentPensions, ("currentPensionsAmt", ""))

                mockAuthConnector(Future.successful({}))
                status(DataItem.result) shouldBe 400
            }
        }
    }


    ///////////////////////////////////////////////
    // PENSION DEBITS
    ///////////////////////////////////////////////
    "In IP2016Controller calling the .pensionDebits action" when {

        "not supplied with a stored model" should {
            "return 200" in new Setup {
                lazy val result = controller.pensionDebits(fakeRequest)
                mockAuthConnector(Future.successful({}))
                keystoreFetchCondition[PensionDebitsModel](None)
                status(result) shouldBe 200
            }

        }

        "supplied with a stored test model" should {
            "return 200" in new Setup {
                val testModel = new PensionDebitsModel(Some("yes"))
                lazy val result = controller.pensionDebits(fakeRequest)

                mockAuthConnector(Future.successful({}))
                keystoreFetchCondition[PensionDebitsModel](Some(testModel))
                status(result) shouldBe 200
            }

            "return some HTML that" should {

                "contain some text and use the character set utf-8" in new Setup {
                    val testModel = new PensionDebitsModel(Some("yes"))
                    lazy val result = controller.pensionDebits(fakeRequest)

                    mockAuthConnector(Future.successful({}))
                    keystoreFetchCondition[PensionDebitsModel](Some(testModel))
                    contentType(result) shouldBe Some("text/html")
                    charset(result) shouldBe Some("utf-8")
                }
            }
        }
    }

    "Submitting Pensions Debits data" when {

        "Submitting 'yes' in pensionDebitsForm" should {
            "redirect to number of pension sharing orders" in new Setup {

                object DataItem extends AuthorisedFakeRequestToPost(controller.submitPensionDebits, ("pensionDebits", "yes"))

                mockAuthConnector(Future.successful({}))
                keystoreSaveCondition[PensionDebitsModel](mockKeyStoreConnector)
                status(DataItem.result) shouldBe 303
                redirectLocation(DataItem.result) shouldBe Some(s"${routes.IP2016Controller.psoDetails()}")
            }
        }

        "Submitting 'no' in pensionDebitsForm" should {
            "redirect to summary" in new Setup {

                object DataItem extends AuthorisedFakeRequestToPost(controller.submitPensionDebits, ("pensionDebits", "no"))

                mockAuthConnector(Future.successful({}))
                keystoreSaveCondition[PensionDebitsModel](mockKeyStoreConnector)
                status(DataItem.result) shouldBe 303
                redirectLocation(DataItem.result) shouldBe Some(s"${routes.SummaryController.summaryIP16()}")
            }
        }

        "Submitting pensionDebitsForm with no data" should {
            "return 400" in new Setup {

                object DataItem extends AuthorisedFakeRequestToPost(controller.submitPensionDebits, ("pensionDebits", ""))

                mockAuthConnector(Future.successful({}))
                status(DataItem.result) shouldBe 400
            }
        }
    }

    ///////////////////////////////////////////////
    // PENSION DETAILS
    ///////////////////////////////////////////////

    "In IP2016Controller calling the .psoDetails action" when {
        "not supplied with a stored model" should {
            "return 200" in new Setup {
                lazy val result = controller.psoDetails(fakeRequest)

                mockAuthConnector(Future.successful({}))
                keystoreFetchCondition[PSODetailsModel](None)
                status(result) shouldBe 200
            }
        }

        "supplied with a stored test model" should {
            "return 200" in new Setup {
                val testModel = PSODetailsModel(1, 8, 2016, BigDecimal(1234))
                lazy val result = controller.psoDetails(fakeRequest)

                mockAuthConnector(Future.successful({}))
                keystoreFetchCondition[PSODetailsModel](Some(testModel))
                status(result) shouldBe 200
            }


            "return some HTML that" should {
                "contain some text and use the character set utf-8" in new Setup {
                    val testModel = PSODetailsModel(1, 8, 2016, BigDecimal(1234))
                    lazy val result = controller.psoDetails(fakeRequest)

                    mockAuthConnector(Future.successful({}))
                    keystoreFetchCondition[PSODetailsModel](Some(testModel))
                    contentType(result) shouldBe Some("text/html")
                    charset(result) shouldBe Some("utf-8")
                }
            }
        }

    }

    "Submitting valid PSO details data" when {

        "submitting valid PSO details" should {
            "redirect to the summary page with a valid PSO" in new Setup {

                object DataItem extends AuthorisedFakeRequestToPost(controller.submitPSODetails,

                    ("psoDay", "6"),
                    ("psoMonth", "4"),
                    ("psoYear", "2016"),
                    ("psoAmt", "100000")
                )

                mockAuthConnector(Future.successful({}))
                keystoreSaveCondition[PensionsTakenModel](mockKeyStoreConnector)
                status(DataItem.result) shouldBe 303
                redirectLocation(DataItem.result) shouldBe Some(s"${routes.SummaryController.summaryIP16()}")
            }
        }

        "submitting an invalid set of PSO details" should {
            "return 400" in new Setup {

                object DataItem extends AuthorisedFakeRequestToPost(controller.submitPSODetails,

                    ("psoDay", ""),
                    ("psoMonth", "1"),
                    ("psoYear", "2015"),
                    ("psoAmt", "100000")
                )
                mockAuthConnector(Future.successful({}))
                status(DataItem.result) shouldBe 400
            }

        }

        "submitting an invalid set of PSO details that fails additional validation" should {
            "return 400" in new Setup {

                object DataItem extends AuthorisedFakeRequestToPost(controller.submitPSODetails,
                    ("psoDay", "35"),
                    ("psoMonth", "1"),
                    ("psoYear", "2015"),
                    ("psoAmt", "100000")
                )
                mockAuthConnector(Future.successful({}))
                status(DataItem.result) shouldBe 400
            }
        }
    }

    "In IP2016Controller calling the .removePsoDetails action" when {

        "supplied with a stored model" should {
            "return 200" in new Setup {
                lazy val result = controller.removePsoDetails(fakeRequest)

                mockAuthConnector(Future.successful({}))
                status(result) shouldBe 200
            }
        }
    }

    "Submitting a pso for removal from application" when {

        "not supplied with a stored model" should {
            "return 303" in new Setup {
                lazy val result = controller.submitRemovePsoDetails(fakeRequest)
                val testModel = new PensionDebitsModel(Some("yes"))

                mockAuthConnector(Future.successful({}))
                pensionsDebitsSaveData(Some(testModel))
                status(result) shouldBe 303
            }
        }
        "not supplied with a stored model" should {
            "redirect location should be the summary page" in new Setup {
                lazy val result = controller.submitRemovePsoDetails(fakeRequest)
                val testModel = new PensionDebitsModel(Some("yes"))

                mockAuthConnector(Future.successful({}))
                pensionsDebitsSaveData(Some(testModel))
                redirectLocation(result) shouldBe Some(s"${routes.SummaryController.summaryIP16()}")
            }
        }
    }
}
