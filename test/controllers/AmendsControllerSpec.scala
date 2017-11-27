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

import java.time.LocalDate
import java.util.UUID

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import auth.MockConfig
import com.kenshoo.play.metrics.PlayModule
import connectors.{KeyStoreConnector, PLAConnector}
import constructors.{DisplayConstructors, ResponseConstructors}
import enums.ApplicationType
import models._
import models.amendModels.{AmendProtectionModel, AmendsGAModel}
import org.jsoup.Jsoup
import org.mockito.Matchers
import org.mockito.Mockito._
import _root_.mock.AuthMock
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mock.MockitoSugar
import play.api.{Configuration, Environment}
import play.api.http.HeaderNames.CACHE_CONTROL
import play.api.i18n.Messages
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers._
import testHelpers._
import uk.gov.hmrc.http.cache.client.CacheMap
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}
import play.api.i18n.Messages.Implicits._
import play.api.Play.current
import uk.gov.hmrc.auth.core.PlayAuthConnector
import uk.gov.hmrc.auth.core.retrieve.{Retrieval, Retrievals}

import scala.concurrent.Future
import uk.gov.hmrc.http.HttpResponse

class AmendsControllerSpec extends UnitSpec with WithFakeApplication with MockitoSugar with KeystoreTestHelper with BeforeAndAfterEach with AuthMock {
  override def bindModules = Seq(new PlayModule)

  val mockKeyStoreConnector = mock[KeyStoreConnector]
  val mockDisplayConstructors = mock[DisplayConstructors]
  val mockPLAConnector = mock[PLAConnector]
  val mockResponseConstructors = mock[ResponseConstructors]
  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()

  override def beforeEach() {
    reset(mockAuthConnector)
  }

  val testIP16DormantModel = AmendProtectionModel(ProtectionModel(None, None), ProtectionModel(None, None, protectionType = Some("IP2016"), status = Some("dormant"), relevantAmount = Some(100000), uncrystallisedRights = Some(100000)))

  object TestAmendsController extends AmendsController  {
    lazy val appConfig = MockConfig
    override lazy val authConnector = mockAuthConnector
    lazy val postSignInRedirectUrl = "http://localhost:9012/protect-your-lifetime-allowance/apply-ip"

    override val displayConstructors: DisplayConstructors = mockDisplayConstructors
    override val keyStoreConnector: KeyStoreConnector = mockKeyStoreConnector
    override val plaConnector: PLAConnector = mockPLAConnector
    override val responseConstructors: ResponseConstructors = mockResponseConstructors

    override def config: Configuration = mock[Configuration]

    override def env: Environment = mock[Environment]
  }

  val sessionId = UUID.randomUUID.toString
  val fakeRequest = FakeRequest()

  val mockUsername = "mockuser"
  val mockUserId = "/auth/oid/" + mockUsername

  val ip2016Protection = ProtectionModel(
    psaCheckReference = Some("testPSARef"),
    uncrystallisedRights = Some(100000.00),
    nonUKRights = Some(2000.00),
    preADayPensionInPayment = Some(2000.00),
    postADayBenefitCrystallisationEvents = Some(2000.00),
    notificationId = Some(12),
    protectionID = Some(12345),
    protectionType = Some("IP2016"),
    status = Some("dormant"),
    certificateDate = Some("2016-04-17"),
    protectedAmount = Some(1250000),
    protectionReference = Some("PSA123456"))

  val testAmendIP2016ProtectionModel = AmendProtectionModel(ip2016Protection, ip2016Protection)


  val ip2014Protection = ProtectionModel(
    psaCheckReference = Some("testPSARef"),
    uncrystallisedRights = Some(100000.00),
    nonUKRights = Some(2000.00),
    preADayPensionInPayment = Some(2000.00),
    postADayBenefitCrystallisationEvents = Some(2000.00),
    notificationId = Some(12),
    protectionID = Some(12345),
    protectionType = Some("IP2014"),
    status = Some("dormant"),
    certificateDate = Some("2016-04-17"),
    protectedAmount = Some(1250000),
    protectionReference = Some("PSA123456"))

  val testAmendIP2014ProtectionModel = AmendProtectionModel(ip2014Protection, ip2014Protection)


  val ip2016NoDebitProtection = ProtectionModel(
    psaCheckReference = Some("testPSARef"),
    uncrystallisedRights = Some(100000.00),
    nonUKRights = Some(0.0),
    preADayPensionInPayment = Some(0.0),
    postADayBenefitCrystallisationEvents = Some(0.0),
    notificationId = Some(12),
    protectionID = Some(12345),
    protectionType = Some("IP2016"),
    status = Some("dormant"),
    certificateDate = Some("2016-04-17"),
    protectedAmount = Some(1250000),
    protectionReference = Some("PSA123456"))
  val testAmendIP2016ProtectionModelWithNoDebit = AmendProtectionModel(ip2016NoDebitProtection, ip2016NoDebitProtection)

  val noNotificationIdProtection = ProtectionModel(
    psaCheckReference = Some("testPSARef"),
    protectionID = Some(12345),
    uncrystallisedRights = Some(100000.00),
    nonUKRights = Some(0.0),
    preADayPensionInPayment = Some(0.0),
    postADayBenefitCrystallisationEvents = Some(0.0),
    protectionType = Some("IP2014"),
    status = Some("dormant"),
    certificateDate = Some("2016-04-17"),
    protectedAmount = Some(1250000),
    protectionReference = Some("PSA123456")
  )

  val tstPensionContributionNoPsoDisplaySections = Seq(

    AmendDisplaySectionModel("PensionsTakenBefore", Seq(
      AmendDisplayRowModel("YesNo", Some(controllers.routes.AmendsController.amendPensionsTakenBefore("ip2014", "active")), None, "No")
    )
    ),
    AmendDisplaySectionModel("PensionsTakenBetween", Seq(
      AmendDisplayRowModel("YesNo", Some(controllers.routes.AmendsController.amendPensionsTakenBetween("ip2014", "active")), None, "No")
    )
    ),
    AmendDisplaySectionModel("OverseasPensions", Seq(
      AmendDisplayRowModel("YesNo", Some(controllers.routes.AmendsController.amendOverseasPensions("ip2014", "active")), None, "Yes"),
      AmendDisplayRowModel("Amt", Some(controllers.routes.AmendsController.amendOverseasPensions("ip2014", "active")), None, "£100,000")
    )
    ),
    AmendDisplaySectionModel("CurrentPensions",Seq(
      AmendDisplayRowModel("Amt", Some(controllers.routes.AmendsController.amendCurrentPensions("ip2014", "active")), None, "£1,000,000")
    )
    ),
    AmendDisplaySectionModel("CurrentPsos", Seq(
      AmendDisplayRowModel("YesNo", None, None, "No")
    )
    )
  )

  val tstAmendDisplayModel = AmendDisplayModel(
    protectionType = "IP2014",
    amended = true,
    pensionContributionSections = tstPensionContributionNoPsoDisplaySections,
    psoAdded = false,
    psoSections = Seq.empty,
    totalAmount = "£1,100,000"
  )

  val ip2014ActiveAmendmentProtection = ProtectionModel(
  psaCheckReference = Some("psaRef"),
  protectionID = Some(12345),
  notificationId = Some(33)
  )
  val tstActiveAmendResponseModel = AmendResponseModel(ip2014ActiveAmendmentProtection)
  val tstActiveAmendResponseDisplayModel = ActiveAmendResultDisplayModel(
    protectionType = ApplicationType.IP2014,
    notificationId = "33",
    protectedAmount = "£1,100,000",
    details = None
  )

  val ip2016InactiveAmendmentProtection = ProtectionModel(
    psaCheckReference = Some("psaRef"),
    protectionID = Some(12345),
    notificationId = Some(43)
  )
  val tstInactiveAmendResponseModel = AmendResponseModel(ip2016InactiveAmendmentProtection)
  val tstInactiveAmendResponseDisplayModel = InactiveAmendResultDisplayModel(
    notificationId = "43",
    additionalInfo = Seq.empty
  )


  def keystoreFetchCondition[T](data: Option[T]): Unit = {
    when(mockKeyStoreConnector.fetchAndGetFormData[T](Matchers.anyString())(Matchers.any(), Matchers.any()))
      .thenReturn(Future.successful(data))
  }



  "In AmendsController calling the amendsSummary action" when {
    "there is no stored amends model" should {
      lazy val result = await(TestAmendsController.amendsSummary("ip2016", "open")(fakeRequest))
      lazy val jsoupDoc = Jsoup.parse(bodyOf(result))
      "return 500" in {
        mockAuthRetrieval[Option[String]](Retrievals.nino, Some("AB123456A"))
        keystoreFetchCondition[AmendProtectionModel](None)
        status(result) shouldBe 500
      }
      "show the technical error page for existing protections" in {
        jsoupDoc.body.getElementsByTag("h1").text shouldEqual Messages("pla.techError.pageHeading")
        jsoupDoc.body.getElementById("tryAgainLink").attr("href") shouldEqual s"${controllers.routes.ReadProtectionsController.currentProtections()}"
      }
      "have the correct cache control" in {
        result.header.headers.getOrElse(CACHE_CONTROL, "No-Cache-Control-Header-Set") shouldBe "no-cache"
      }
    }

    "there is a stored, updated amends model" should {
      lazy val result = await(TestAmendsController.amendsSummary("ip2014", "dormant")(fakeRequest))
      lazy val jsoupDoc = Jsoup.parse(bodyOf(result))
      "return 200" in {
        mockAuthRetrieval[Option[String]](Retrievals.nino, Some("AB123456A"))
        keystoreFetchCondition[AmendProtectionModel](Some(testAmendIP2014ProtectionModel))
        when(mockDisplayConstructors.createAmendDisplayModel(Matchers.any())(Matchers.any())).thenReturn(tstAmendDisplayModel)
        status(result) shouldBe 200
      }
      "show the amends page for an updated protection for IP2014" in {
        jsoupDoc.body.getElementsByTag("h1").text shouldEqual Messages("pla.amends.heading.IP2014.changed")
      }
    }
  }

  "Calling the amendProtection action" when {
    "the hidden fields in the amendment summary page have not been populated correctly" should {
      object DataItem extends AuthorisedFakeRequestToPost(TestAmendsController.amendProtection, ("protectionTypez", "stuff"))
      "return 500" in {
        mockAuthRetrieval[Option[String]](Retrievals.nino, Some("AB123456A"))
        keystoreFetchCondition[AmendProtectionModel](Some(testAmendIP2014ProtectionModel))
        status(DataItem.result) shouldBe 500
      }
      "show the technical error page for existing protections" in {
        mockAuthRetrieval[Option[String]](Retrievals.nino, Some("AB123456A"))
        keystoreFetchCondition[AmendProtectionModel](Some(testAmendIP2014ProtectionModel))
        DataItem.jsoupDoc.body.getElementsByTag("h1").text shouldEqual Messages("pla.techError.pageHeading")
        DataItem.jsoupDoc.body.getElementById("tryAgainLink").attr("href") shouldEqual s"${controllers.routes.ReadProtectionsController.currentProtections()}"
      }
      "have the correct cache control" in {DataItem.result.header.headers.getOrElse(CACHE_CONTROL, "No-Cache-Control-Header-Set") shouldBe "no-cache" }
    }

    "the microservice returns a conflict response" should {
      object DataItem extends AuthorisedFakeRequestToPost(TestAmendsController.amendProtection, ("protectionType", "IP2014"), ("status", "dormant"))
      "return 500" in {
        mockAuthRetrieval[Option[String]](Retrievals.nino, Some("AB123456A"))
        keystoreFetchCondition[AmendProtectionModel](Some(testAmendIP2014ProtectionModel))
        when(mockKeyStoreConnector.saveData(Matchers.anyString(), Matchers.any())(Matchers.any(), Matchers.any())).thenReturn(Future.successful(CacheMap("GA", Map.empty)))
        when(mockPLAConnector.amendProtection(Matchers.any(), Matchers.any())(Matchers.any())).thenReturn(Future.successful(HttpResponse(409)))
        status(DataItem.result) shouldBe 500
      }
      "show the technical error page for existing protections" in {
        DataItem.jsoupDoc.body.getElementsByTag("h1").text shouldEqual Messages("pla.techError.pageHeading")
        DataItem.jsoupDoc.body.getElementById("tryAgainLink").attr("href") shouldEqual s"${controllers.routes.ReadProtectionsController.currentProtections()}"
      }
      "have the correct cache control" in {
        DataItem.result.header.headers.getOrElse(CACHE_CONTROL, "No-Cache-Control-Header-Set") shouldBe "no-cache" }
    }

    "the microservice returns a manual correspondence needed response" should {
      object DataItem extends AuthorisedFakeRequestToPost(TestAmendsController.amendProtection, ("protectionType", "IP2014"), ("status", "dormant"))
      "return a Locked response" in {
        mockAuthRetrieval[Option[String]](Retrievals.nino, Some("AB123456A"))
        keystoreFetchCondition[AmendProtectionModel](Some(testAmendIP2014ProtectionModel))
        when(mockPLAConnector.amendProtection(Matchers.any(), Matchers.any())(Matchers.any())).thenReturn(Future.successful(HttpResponse(423)))
        status(DataItem.result) shouldBe 423
      }

      "show the MC Needed page" in {
        DataItem.jsoupDoc.body.getElementsByTag("h1").text shouldEqual Messages("pla.mcNeeded.pageHeading")
      }
    }

    "the microservice returns an invalid json response" should {
      object DataItem extends AuthorisedFakeRequestToPost(TestAmendsController.amendProtection, ("protectionType", "IP2014"), ("status", "dormant"))
      "return 500" in {
        mockAuthRetrieval[Option[String]](Retrievals.nino, Some("AB123456A"))
        keystoreFetchCondition[AmendProtectionModel](Some(testAmendIP2014ProtectionModel))
        when(mockPLAConnector.amendProtection(Matchers.any(), Matchers.any())(Matchers.any()))
          .thenReturn(Future.successful(HttpResponse(200, responseJson = Some(Json.parse("""{"result":"doesNotMatter"}""")))))
        when(mockResponseConstructors.createAmendResponseModelFromJson(Matchers.any())).thenReturn(None)
        status(DataItem.result) shouldBe 500
      }
      "show the technical error page for existing protections" in {
        DataItem.jsoupDoc.body.getElementsByTag("h1").text shouldEqual Messages("pla.techError.pageHeading")
        DataItem.jsoupDoc.body.getElementById("tryAgainLink").attr("href") shouldEqual s"${controllers.routes.ReadProtectionsController.currentProtections()}"
      }
      "have the correct cache control" in {DataItem.result.header.headers.getOrElse(CACHE_CONTROL, "No-Cache-Control-Header-Set") shouldBe "no-cache" }
    }

    "the microservice returns a response with no notificationId" should {
      object DataItem extends AuthorisedFakeRequestToPost(TestAmendsController.amendProtection, ("protectionType", "IP2014"), ("status", "dormant"))
      "return 500" in {
        mockAuthRetrieval[Option[String]](Retrievals.nino, Some("AB123456A"))
        keystoreFetchCondition[AmendProtectionModel](Some(testAmendIP2014ProtectionModel))
        when(mockPLAConnector.amendProtection(Matchers.any(), Matchers.any())(Matchers.any()))
          .thenReturn(Future.successful(HttpResponse(200, responseJson = Some(Json.parse("""{"result":"doesNotMatter"}""")))))
        when(mockResponseConstructors.createAmendResponseModelFromJson(Matchers.any())).thenReturn(Some(AmendResponseModel(noNotificationIdProtection)))
        status(DataItem.result) shouldBe 500
      }
      "show the technical error page for no notification ID" in {
        DataItem.jsoupDoc.body.getElementsByTag("h1").text shouldEqual Messages("pla.noNotificationId.title")
        DataItem.jsoupDoc.body.getElementById("existingProtectionsLink").attr("href") shouldEqual s"${controllers.routes.ReadProtectionsController.currentProtections()}"
      }
      "have the correct cache control" in {DataItem.result.header.headers.getOrElse(CACHE_CONTROL, "No-Cache-Control-Header-Set") shouldBe "no-cache" }
    }

    "the microservice returns a valid response" should {
      object DataItem extends AuthorisedFakeRequestToPost(TestAmendsController.amendProtection, ("protectionType", "IP2014"), ("status", "dormant"))
      "return 303" in {
        mockAuthRetrieval[Option[String]](Retrievals.nino, Some("AB123456A"))
        keystoreFetchCondition[AmendProtectionModel](Some(testAmendIP2014ProtectionModel))
        when(mockPLAConnector.amendProtection(Matchers.any(), Matchers.any())(Matchers.any()))
          .thenReturn(Future.successful(HttpResponse(200, responseJson = Some(Json.parse("""{"result":"doesNotMatter"}""")))))
        when(mockResponseConstructors.createAmendResponseModelFromJson(Matchers.any())).thenReturn(Some(tstActiveAmendResponseModel))
        when(mockKeyStoreConnector.saveData(Matchers.anyString(), Matchers.any())(Matchers.any(), Matchers.any()))
          .thenReturn(Future.successful(CacheMap("keyStoreId", Map.empty)))
        status(DataItem.result) shouldBe 303
      }
      "redirect to amendment outcome" in {
        redirectLocation(DataItem.result) shouldBe Some(s"${routes.AmendsController.amendmentOutcome()}")
      }
    }

  }

  "Calling the amendmentOutcome action" when {
    "there is no outcome object stored in keystore" should {
      lazy val result = await(TestAmendsController.amendmentOutcome()(fakeRequest))
      lazy val jsoupDoc = Jsoup.parse(bodyOf(result))
      "return 500" in {
        mockAuthRetrieval[Option[String]](Retrievals.nino, Some("AB123456A"))
        keystoreFetchCondition[AmendResponseModel](None)
        keystoreFetchCondition[AmendsGAModel](None)
        status(result) shouldBe 500
      }
      "show the technical error page for existing protections" in {
        jsoupDoc.body.getElementsByTag("h1").text shouldEqual Messages("pla.techError.pageHeading")
        jsoupDoc.body.getElementById("tryAgainLink").attr("href") shouldEqual s"${controllers.routes.ReadProtectionsController.currentProtections()}"
      }
      "have the correct cache control" in {result.header.headers.getOrElse(CACHE_CONTROL, "No-Cache-Control-Header-Set") shouldBe "no-cache" }
    }

    "there is an active protection outcome in keystore" should {
      lazy val result = await(TestAmendsController.amendmentOutcome()(fakeRequest))
      lazy val jsoupDoc = Jsoup.parse(bodyOf(result))
      "return 200" in {
        mockAuthRetrieval[Option[String]](Retrievals.nino, Some("AB123456A"))
        when(mockKeyStoreConnector.fetchAndGetFormData[AmendResponseModel](Matchers.startsWith("amendResponseModel"))(Matchers.any(), Matchers.any())).thenReturn(Future.successful(Some(tstActiveAmendResponseModel)))
        when(mockKeyStoreConnector.fetchAndGetFormData[AmendsGAModel](Matchers.startsWith("AmendsGA"))(Matchers.any(), Matchers.any())).thenReturn(Future.successful(Some(AmendsGAModel(Some("updatedValue"),Some("changedToYes"),Some("changedToNo"),None,Some("addedPSO")))))
        when(mockDisplayConstructors.createActiveAmendResponseDisplayModel(Matchers.any())).thenReturn(tstActiveAmendResponseDisplayModel)
        status(result) shouldBe 200
      }

      "show the active amendment result page" in {
        jsoupDoc.body.getElementById("amendmentOutcome").text shouldEqual Messages("amendResultCode.33.heading")
      }
    }

    "there is an inactive protection outcome in keystore" should {
      lazy val result = await(TestAmendsController.amendmentOutcome()(fakeRequest))
      lazy val jsoupDoc = Jsoup.parse(bodyOf(result))
      "return 200" in {
        mockAuthRetrieval[Option[String]](Retrievals.nino, Some("AB123456A"))

        when(mockKeyStoreConnector.fetchAndGetFormData[AmendResponseModel](Matchers.startsWith("amendResponseModel"))(Matchers.any(), Matchers.any())).thenReturn(Future.successful(Some(tstInactiveAmendResponseModel)))
        when(mockKeyStoreConnector.fetchAndGetFormData[AmendsGAModel](Matchers.startsWith("AmendsGA"))(Matchers.any(), Matchers.any())).thenReturn(Future.successful(Some(AmendsGAModel(None,Some("changedToNo"),Some("changedToYes"),None,None))))
        when(mockDisplayConstructors.createInactiveAmendResponseDisplayModel(Matchers.any())).thenReturn(tstInactiveAmendResponseDisplayModel)
        status(result) shouldBe 200
      }

      "show the inactive amendment result page" in {
        jsoupDoc.body.getElementById("resultPageHeading").text shouldEqual Messages("amendResultCode.43.heading")
      }
    }

  }

  "Calling the .amendCurrentPensions action" when {

    "not supplied with a stored model" should {

      lazy val result = await(TestAmendsController.amendCurrentPensions("ip2016", "open")(fakeRequest))
      lazy val jsoupDoc = Jsoup.parse(bodyOf(result))
      "return 500" in {
        mockAuthRetrieval[Option[String]](Retrievals.nino, Some("AB123456A"))
        keystoreFetchCondition[AmendProtectionModel](None)
        status(result) shouldBe 500
      }
    }

    "supplied with a stored test model (£100000, IP2016, dormant)" should {
      val testModel = new AmendProtectionModel(ProtectionModel(None, None), ProtectionModel(None, None, uncrystallisedRights = Some(100000)))
      lazy val result = await(TestAmendsController.amendCurrentPensions("ip2016", "dormant")(fakeRequest))
      lazy val jsoupDoc = Jsoup.parse(bodyOf(result))

      "return 200" in {
        mockAuthRetrieval[Option[String]](Retrievals.nino, Some("AB123456A"))
        keystoreFetchCondition[AmendProtectionModel](Some(testModel))
        status(result) shouldBe 200
      }

      "take the user to the amend ip16 current pensions page" in {
        keystoreFetchCondition[AmendProtectionModel](Some(testModel))
        jsoupDoc.body.getElementsByTag("h1").text shouldEqual Messages("pla.currentPensions.title")
      }

      "return some HTML that" should {

        "contain some text and use the character set utf-8" in {
          mockAuthRetrieval[Option[String]](Retrievals.nino, Some("AB123456A"))
          keystoreFetchCondition[AmendProtectionModel](Some(testModel))
          contentType(result) shouldBe Some("text/html")
          charset(result) shouldBe Some("utf-8")
        }

        "have the value 100000 completed in the amount input by default" in {
          mockAuthRetrieval[Option[String]](Retrievals.nino, Some("AB123456A"))
          keystoreFetchCondition[AmendProtectionModel](Some(testModel))
          jsoupDoc.body.getElementById("amendedUKPensionAmt").attr("value") shouldBe "100000"
        }
      }
    }

    "supplied with a stored test model (£100000, IP2014, dormant)" should {
      lazy val result = await(TestAmendsController.amendCurrentPensions("ip2016", "dormant")(fakeRequest))
      lazy val jsoupDoc = Jsoup.parse(bodyOf(result))
      "return 200" in {
        mockAuthRetrieval[Option[String]](Retrievals.nino, Some("AB123456A"))
        keystoreFetchCondition[AmendProtectionModel](Some(testAmendIP2014ProtectionModel))
        status(result) shouldBe 200
      }
    }
  }

  "Submitting Amend IP16 Current Pensions data" when {

    "the data is valid" should {

      object DataItem extends AuthorisedFakeRequestToPost(TestAmendsController.submitAmendCurrentPension, ("amendedUKPensionAmt", "100000"), ("protectionType", "ip2016"), ("status", "dormant"))
      "return 303" in {

      }
      "redirect to Amends Summary page" in {
        mockAuthRetrieval[Option[String]](Retrievals.nino, Some("AB123456A"))
        keystoreSaveCondition[AmendProtectionModel](mockKeyStoreConnector)
        keystoreFetchCondition[AmendProtectionModel](Some(testIP16DormantModel))
        status(DataItem.result) shouldBe 303
        redirectLocation(DataItem.result) shouldBe Some(s"${routes.AmendsController.amendsSummary("ip2016", "dormant")}")
      }
    }

    "the data is invalid" should {

      object DataItem extends AuthorisedFakeRequestToPost(TestAmendsController.submitAmendCurrentPension, ("amendedUKPensionAmt", ""))
      "return 400" in {
        mockAuthRetrieval[Option[String]](Retrievals.nino, Some("AB123456A"))
        status(DataItem.result) shouldBe 400
      }
    }

    "the model can't be fetched from keyStore" should {
      object DataItem extends AuthorisedFakeRequestToPost(TestAmendsController.submitAmendCurrentPension, ("amendedUKPensionAmt", "1000000"), ("protectionType", "IP2016"), ("status", "dormant"))

      "return 500" in {
        mockAuthRetrieval[Option[String]](Retrievals.nino, Some("AB123456A"))
        keystoreFetchCondition[AmendProtectionModel](None)
        status(DataItem.result) shouldBe 500
      }
    }

  }

  "In AmendsController calling the .amendPensionsTakenBefore action" when {

    "not supplied with a stored model" should {

      lazy val result = await(TestAmendsController.amendPensionsTakenBefore("ip2016", "open")(fakeRequest))
      lazy val jsoupDoc = Jsoup.parse(bodyOf(result))
      "return 500" in {
        mockAuthRetrieval[Option[String]](Retrievals.nino, Some("AB123456A"))
        keystoreFetchCondition[AmendProtectionModel](None)
        status(result) shouldBe 500
      }
    }
    "supplied with the stored test model for (dormant, IP2016, preADay = £0.0)" should {
      lazy val result = await(TestAmendsController.amendPensionsTakenBefore("ip2016", "dormant")(fakeRequest))
      lazy val jsoupDoc = Jsoup.parse(bodyOf(result))
      "have the value of the check box set as 'No' by default" in {
        mockAuthRetrieval[Option[String]](Retrievals.nino, Some("AB123456A"))
        keystoreFetchCondition[AmendProtectionModel](Some(testAmendIP2016ProtectionModelWithNoDebit))
        jsoupDoc.body.getElementById("amendedPensionsTakenBefore-no").attr("checked") shouldBe "checked"
      }
    }

    "supplied with the stored test model for (dormant, IP2016, preADay = £2000)" should {

      lazy val result = await(TestAmendsController.amendPensionsTakenBefore("ip2016", "dormant")(fakeRequest))
      lazy val jsoupDoc = Jsoup.parse(bodyOf(result))
      "return 200" in {
        mockAuthRetrieval[Option[String]](Retrievals.nino, Some("AB123456A"))
        keystoreFetchCondition[AmendProtectionModel](Some(testAmendIP2016ProtectionModel))
        status(result) shouldBe 200
      }

      "should take the user to the pensions taken before page" in {
        keystoreFetchCondition[AmendProtectionModel](Some(testAmendIP2016ProtectionModel))
        jsoupDoc.body.getElementsByTag("h1").text shouldEqual Messages("pla.pensionsTakenBefore.title")
      }

      "return some HTML that" should {

        "contain some text and use the character set utf-8" in {
          keystoreFetchCondition[AmendProtectionModel](Some(testAmendIP2016ProtectionModel))
          contentType(result) shouldBe Some("text/html")
          charset(result) shouldBe Some("utf-8")
        }

        "have the value of the check box set as 'Yes' by default" in {
          keystoreFetchCondition[AmendProtectionModel](Some(testAmendIP2016ProtectionModel))
          jsoupDoc.body.getElementById("amendedPensionsTakenBefore-yes").attr("checked") shouldBe "checked"
        }

        "have the value of the input field set to 2000 by default" in {
          keystoreFetchCondition[AmendProtectionModel](Some(testAmendIP2016ProtectionModel))
          jsoupDoc.body.getElementById("amendedPensionsTakenBeforeAmt").attr("value") shouldBe "2000"
        }
      }
    }

    "supplied with the stored test model for (dormant, IP2014, preADay = £2000)" should {
      lazy val result = await(TestAmendsController.amendPensionsTakenBefore("ip2016", "dormant")(fakeRequest))
      lazy val jsoupDoc = Jsoup.parse(bodyOf(result))
      "return 200" in {
        mockAuthRetrieval[Option[String]](Retrievals.nino, Some("AB123456A"))
        keystoreFetchCondition[AmendProtectionModel](Some(testAmendIP2014ProtectionModel))
        status(result) shouldBe 200
      }
    }
  }

  "Submitting Amend IP16 Pensions Taken Before data" when {

    "the data is invalid" should {
  lazy val result = await(TestAmendsController.submitAmendPensionsTakenBefore(fakeRequest))
  lazy val jsoupDoc = Jsoup.parse(bodyOf(result))
      "return 400" in {
        mockAuthRetrieval[Option[String]](Retrievals.nino, Some("AB123456A"))
        status(result) shouldBe 400
      }
    }

    "the data is invalidated by additional validation" should {
      object DataItem extends AuthorisedFakeRequestToPost(TestAmendsController.submitAmendPensionsTakenBefore,
        ("amendedPensionsTakenBefore", "yes"), ("amendedPensionsTakenBeforeAmt", "-1"), ("protectionType", "ip2016"), ("status", "dormant"))

      "return 400" in {
        mockAuthRetrieval[Option[String]](Retrievals.nino, Some("AB123456A"))
        status(DataItem.result) shouldBe 400
      }
    }

    "the model can't be fetched from keyStore" should {
      object DataItem extends AuthorisedFakeRequestToPost(TestAmendsController.submitAmendPensionsTakenBefore,
        ("amendedPensionsTakenBefore", "no"), ("amendedPensionsTakenBeforeAmt", "0"), ("protectionType", "ip2016"), ("status", "dormant"))

      "return 500" in {
        mockAuthRetrieval[Option[String]](Retrievals.nino, Some("AB123456A"))
        keystoreFetchCondition[AmendProtectionModel](None)
        status(DataItem.result) shouldBe 500
      }
    }

    "the data is valid with a no" should {
      object DataItem extends AuthorisedFakeRequestToPost(TestAmendsController.submitAmendPensionsTakenBefore,
        ("amendedPensionsTakenBefore", "no"), ("amendedPensionsTakenBeforeAmt", "0"), ("protectionType", "ip2016"), ("status", "dormant"))


      "redirect to Amends Summary Page" in {
        keystoreSaveCondition[PensionsTakenBeforeModel](mockKeyStoreConnector)
        keystoreFetchCondition[AmendProtectionModel](Some(testAmendIP2016ProtectionModel))
        status(DataItem.result) shouldBe 303
        redirectLocation(DataItem.result) shouldBe Some(s"${routes.AmendsController.amendsSummary("ip2016", "dormant")}")
      }
    }

    "the data is valid with a yes" should {
      object DataItem extends AuthorisedFakeRequestToPost(TestAmendsController.submitAmendPensionsTakenBefore,
        ("amendedPensionsTakenBefore", "yes"), ("amendedPensionsTakenBeforeAmt", "10"), ("protectionType", "ip2016"), ("status", "dormant"))

      "redirect to Amends Summary Page" in {
        keystoreSaveCondition[PensionsTakenBeforeModel](mockKeyStoreConnector)
        keystoreFetchCondition[AmendProtectionModel](Some(testAmendIP2016ProtectionModel))
        status(DataItem.result) shouldBe 303
        redirectLocation(DataItem.result) shouldBe Some(s"${routes.AmendsController.amendsSummary("ip2016", "dormant")}")
      }
    }
  }

  "In AmendsController calling the .amendPensionsTakenBetween action" when {
    "not supplied with a stored model" should {

      lazy val result = await(TestAmendsController.amendPensionsTakenBetween("ip2016", "open")(fakeRequest))
      lazy val jsoupDoc = Jsoup.parse(bodyOf(result))
      "return 500" in {
        mockAuthRetrieval[Option[String]](Retrievals.nino, Some("AB123456A"))
        keystoreFetchCondition[AmendProtectionModel](None)
        status(result) shouldBe 500
      }
    }
    "supplied with the stored test model for (dormant, IP2016, preADay = £0.0)" should {
      lazy val result = await(TestAmendsController.amendPensionsTakenBetween("ip2016", "dormant")(fakeRequest))
      lazy val jsoupDoc = Jsoup.parse(bodyOf(result))

      "have the value of the check box set as 'No' by default" in {
        mockAuthRetrieval[Option[String]](Retrievals.nino, Some("AB123456A"))
        keystoreFetchCondition[AmendProtectionModel](Some(testAmendIP2016ProtectionModelWithNoDebit))
        jsoupDoc.body.getElementById("amendedPensionsTakenBetween-no").attr("checked") shouldBe "checked"
      }
    }

    "supplied with the stored test model for (dormant, IP2016, preADay = £2000)" should {

      lazy val result = await(TestAmendsController.amendPensionsTakenBetween("ip2016", "dormant")(fakeRequest))
      lazy val jsoupDoc = Jsoup.parse(bodyOf(result))
      "return 200" in {
        mockAuthRetrieval[Option[String]](Retrievals.nino, Some("AB123456A"))
        keystoreFetchCondition[AmendProtectionModel](Some(testAmendIP2016ProtectionModel))
        status(result) shouldBe 200
      }

      "should take the user to the pensions taken before page" in {
        keystoreFetchCondition[AmendProtectionModel](Some(testAmendIP2016ProtectionModel))
        jsoupDoc.body.getElementsByTag("h1").text shouldEqual Messages("pla.pensionsTakenBetween.title")
      }

      "return some HTML that" should {

        "contain some text and use the character set utf-8" in {
          keystoreFetchCondition[AmendProtectionModel](Some(testAmendIP2016ProtectionModel))
          contentType(result) shouldBe Some("text/html")
          charset(result) shouldBe Some("utf-8")
        }

        "have the value of the check box set as 'Yes' by default" in {
          keystoreFetchCondition[AmendProtectionModel](Some(testAmendIP2016ProtectionModel))
          jsoupDoc.body.getElementById("amendedPensionsTakenBetween-yes").attr("checked") shouldBe "checked"
        }

        "have the value of the input field set to 2000 by default" in {
          keystoreFetchCondition[AmendProtectionModel](Some(testAmendIP2016ProtectionModel))
          jsoupDoc.body.getElementById("amendedPensionsTakenBetweenAmt").attr("value") shouldBe "2000"
        }
      }
    }

    "supplied with the stored test model for (dormant, IP2014, preADay = £2000)" should {
      lazy val result = await(TestAmendsController.amendPensionsTakenBetween("ip2014", "dormant")(fakeRequest))
      lazy val jsoupDoc = Jsoup.parse(bodyOf(result))
      "return 200" in {
        mockAuthRetrieval[Option[String]](Retrievals.nino, Some("AB123456A"))
        keystoreFetchCondition[AmendProtectionModel](Some(testAmendIP2014ProtectionModel))
        status(result) shouldBe 200
      }
    }

  }

  "Submitting Amend IP16 Pensions Taken Between data" when {

    "the model can't be fetched from keyStore" should {
      object DataItem extends AuthorisedFakeRequestToPost(TestAmendsController.submitAmendPensionsTakenBetween,
        ("amendedPensionsTakenBetween", "no"), ("amendedPensionsTakenBetweenAmt", "0"), ("protectionType", "ip2016"), ("status", "dormant"))

      "return 500" in {
        mockAuthRetrieval[Option[String]](Retrievals.nino, Some("AB123456A"))
        keystoreFetchCondition[AmendProtectionModel](None)
        status(DataItem.result) shouldBe 500
      }
    }

    "the data is valid with a no response" should {
      object DataItem extends AuthorisedFakeRequestToPost(TestAmendsController.submitAmendPensionsTakenBetween,
        ("amendedPensionsTakenBetween", "no"), ("amendedPensionsTakenBetweenAmt", "0"), ("protectionType", "ip2016"), ("status", "dormant"))

      "return 303" in {
        mockAuthRetrieval[Option[String]](Retrievals.nino, Some("AB123456A"))
        keystoreFetchCondition[AmendProtectionModel](Some(testAmendIP2016ProtectionModel))
        status(DataItem.result) shouldBe 303
      }
      "redirect to Amends Summary Page" in {
        redirectLocation(DataItem.result) shouldBe Some(s"${routes.AmendsController.amendsSummary("ip2016", "dormant")}")
      }
    }

    "the data is valid with a yes response" should {
      object DataItem extends AuthorisedFakeRequestToPost(TestAmendsController.submitAmendPensionsTakenBetween,
        ("amendedPensionsTakenBetween", "yes"), ("amendedPensionsTakenBetweenAmt", "10"), ("protectionType", "ip2016"), ("status", "dormant"))

      "return 303" in {
        mockAuthRetrieval[Option[String]](Retrievals.nino, Some("AB123456A"))
        keystoreFetchCondition[AmendProtectionModel](Some(testAmendIP2016ProtectionModel))
        status(DataItem.result) shouldBe 303
      }
      "redirect to Amends Summary Page" in {
        redirectLocation(DataItem.result) shouldBe Some(s"${routes.AmendsController.amendsSummary("ip2016", "dormant")}")
      }
    }

    "the data is invalid" should {
      object DataItem extends AuthorisedFakeRequestToPost(TestAmendsController.submitAmendPensionsTakenBetween,
        ("amendedPensionsTakenBetweenAmt", ""), ("protectionType", "ip2016"), ("status", "dormant"))

      "return 400" in {
        mockAuthRetrieval[Option[String]](Retrievals.nino, Some("AB123456A"))
        status(DataItem.result) shouldBe 400
      }
    }

    "the data is invalid on additional validation" should {
      object DataItem extends AuthorisedFakeRequestToPost(TestAmendsController.submitAmendPensionsTakenBetween,
        ("amendedPensionsTakenBetween", "yes"), ("amendedPensionsTakenBetweenAmt", ""), ("protectionType", "ip2016"), ("status", "dormant"))

      "return 400" in {
        mockAuthRetrieval[Option[String]](Retrievals.nino, Some("AB123456A"))
        status(DataItem.result) shouldBe 400
      }
    }
  }

  "In AmendsController calling the .amendOverseasPensions action" when {

    "not supplied with a stored model" should {

      lazy val result = await(TestAmendsController.amendOverseasPensions("ip2016", "open")(fakeRequest))
      lazy val jsoupDoc = Jsoup.parse(bodyOf(result))
      "return 500" in {
        mockAuthRetrieval[Option[String]](Retrievals.nino, Some("AB123456A"))
        keystoreFetchCondition[AmendProtectionModel](None)
        status(result) shouldBe 500
      }
    }
    "supplied with the stored test model for (dormant, IP2016, nonUKRights = £0.0)" should {
      lazy val result = await(TestAmendsController.amendOverseasPensions("ip2016", "dormant")(fakeRequest))
      lazy val jsoupDoc = Jsoup.parse(bodyOf(result))

      "have the value of the check box set as 'No' by default" in {
        mockAuthRetrieval[Option[String]](Retrievals.nino, Some("AB123456A"))
        keystoreFetchCondition[AmendProtectionModel](Some(testAmendIP2016ProtectionModelWithNoDebit))
        jsoupDoc.body.getElementById("amendedOverseasPensions-no").attr("checked") shouldBe "checked"
      }
    }

    "supplied with the stored test model for (dormant, IP2016, nonUKRights = £2000)" should {

      lazy val result = await(TestAmendsController.amendOverseasPensions("ip2016", "dormant")(fakeRequest))
      lazy val jsoupDoc = Jsoup.parse(bodyOf(result))
      "return 200" in {
        mockAuthRetrieval[Option[String]](Retrievals.nino, Some("AB123456A"))
        keystoreFetchCondition[AmendProtectionModel](Some(testAmendIP2016ProtectionModel))
        status(result) shouldBe 200
      }

      "should take the user to the overseas pensions page" in {
        keystoreFetchCondition[AmendProtectionModel](Some(testAmendIP2016ProtectionModel))
        jsoupDoc.body.getElementsByTag("h1").text shouldEqual Messages("pla.overseasPensions.title")
      }

      "return some HTML that" should {

        "contain some text and use the character set utf-8" in {
          keystoreFetchCondition[AmendProtectionModel](Some(testAmendIP2016ProtectionModel))
          contentType(result) shouldBe Some("text/html")
          charset(result) shouldBe Some("utf-8")
        }

        "have the value of the check box set as 'Yes' by default" in {
          keystoreFetchCondition[AmendProtectionModel](Some(testAmendIP2016ProtectionModel))
          jsoupDoc.body.getElementById("amendedOverseasPensions-yes").attr("checked") shouldBe "checked"
        }

        "have the value of the input field set to 2000 by default" in {
          keystoreFetchCondition[AmendProtectionModel](Some(testAmendIP2016ProtectionModel))
          jsoupDoc.body.getElementById("amendedOverseasPensionsAmt").attr("value") shouldBe "2000"
        }
      }
    }

    "supplied with the stored test model for (dormant, IP2014, nonUKRights = £2000)" should {
      lazy val result = await(TestAmendsController.amendOverseasPensions("ip2014", "dormant")(fakeRequest))
      lazy val jsoupDoc = Jsoup.parse(bodyOf(result))
      "return 200" in {
        mockAuthRetrieval[Option[String]](Retrievals.nino, Some("AB123456A"))
        keystoreFetchCondition[AmendProtectionModel](Some(testAmendIP2014ProtectionModel))
        status(result) shouldBe 200
      }
    }
  }


  "Submitting Amend IP16 Overseas Pensions data" when {

    "there is an error reading the form" should {
      lazy val result = await(TestAmendsController.submitAmendOverseasPensions(fakeRequest))
      lazy val jsoupDoc = Jsoup.parse(bodyOf(result))
      "return 400" in {
        mockAuthRetrieval[Option[String]](Retrievals.nino, Some("AB123456A"))
        status(result) shouldBe 400
      }
    }

    "the model can't be fetched from keyStore" should {
      object DataItem extends AuthorisedFakeRequestToPost(TestAmendsController.submitAmendOverseasPensions,
        ("amendedOverseasPensions", "no"), ("amendedOverseasPensionsAmt", "0"), ("protectionType", "ip2016"), ("status", "dormant"))

      "return 500" in {
        mockAuthRetrieval[Option[String]](Retrievals.nino, Some("AB123456A"))
        keystoreFetchCondition[AmendProtectionModel](None)
        status(DataItem.result) shouldBe 500
      }
    }

    "the data is valid with a no response" should {
      object DataItem extends AuthorisedFakeRequestToPost(TestAmendsController.submitAmendOverseasPensions,
        ("amendedOverseasPensions", "no"), ("amendedOverseasPensionsAmt", "0"), ("protectionType", "ip2016"), ("status", "dormant"))

      "return 303" in {
        mockAuthRetrieval[Option[String]](Retrievals.nino, Some("AB123456A"))
        keystoreFetchCondition[AmendProtectionModel](Some(testAmendIP2016ProtectionModel))
        status(DataItem.result) shouldBe 303
      }
      "redirect to Amends Summary Page" in {
        redirectLocation(DataItem.result) shouldBe Some(s"${routes.AmendsController.amendsSummary("ip2016", "dormant")}")
      }
    }

    "the data is valid with a yes response" should {
      object DataItem extends AuthorisedFakeRequestToPost(TestAmendsController.submitAmendOverseasPensions,
        ("amendedOverseasPensions", "yes"), ("amendedOverseasPensionsAmt", "10"), ("protectionType", "ip2016"), ("status", "dormant"))

      "return 303" in {
        mockAuthRetrieval[Option[String]](Retrievals.nino, Some("AB123456A"))
        keystoreFetchCondition[AmendProtectionModel](Some(testAmendIP2016ProtectionModel))
        status(DataItem.result) shouldBe 303
      }
      "redirect to Amends Summary Page" in {
        redirectLocation(DataItem.result) shouldBe Some(s"${routes.AmendsController.amendsSummary("ip2016", "dormant")}")
      }
    }

    "the data is invalid" should {
      object DataItem extends AuthorisedFakeRequestToPost(TestAmendsController.submitAmendOverseasPensions,
        ("amendedOverseasPensions", "yes"), ("amendedOverseasPensionsAmt", ""), ("protectionType", "ip2016"), ("status", "dormant"))

      "return 400" in {
        mockAuthRetrieval[Option[String]](Retrievals.nino, Some("AB123456A"))
        status(DataItem.result) shouldBe 400
      }
      "fail with the correct error message" in {
        DataItem.jsoupDoc.getElementsByClass("error-notification").text should include(Messages("pla.base.errors.errorQuestion"))
      }
    }
  }

  "Calling the amendPsoDetails action" when {

    val testProtectionNoPsoList = ProtectionModel (
      psaCheckReference = Some("psaRef"),
      protectionID = Some(1234),
      pensionDebits = None
    )

    val testProtectionEmptyPsoList = ProtectionModel (
      psaCheckReference = Some("psaRef"),
      protectionID = Some(1234),
      pensionDebits = Some(List.empty)
    )

    val testProtectionSinglePsoList = ProtectionModel (
      psaCheckReference = Some("psaRef"),
      protectionID = Some(1234),
      pensionDebits = Some(List(PensionDebitModel("2016-12-23", 1000.0)))
    )

    val testProtectionMultiplePsoList = ProtectionModel (
      psaCheckReference = Some("psaRef"),
      protectionID = Some(1234),
      pensionDebits = Some(List(PensionDebitModel("2016-12-23", 1000.0), PensionDebitModel("2016-12-27", 11322.75)))
    )

    "there is no amendment model fetched from keystore" should {

      lazy val result = await(TestAmendsController.amendPsoDetails("ip2014", "open")(fakeRequest))
      lazy val jsoupDoc = Jsoup.parse(bodyOf(result))

      "return 500" in {
        mockAuthRetrieval[Option[String]](Retrievals.nino, Some("AB123456A"))
        keystoreFetchCondition[AmendProtectionModel](None)
        status(result) shouldBe 500
      }
      "show the technical error page for existing protections" in {
        jsoupDoc.body.getElementsByTag("h1").text shouldEqual Messages("pla.techError.pageHeading")
        jsoupDoc.body.getElementById("tryAgainLink").attr("href") shouldEqual s"${controllers.routes.ReadProtectionsController.currentProtections()}"
      }
      "have the correct cache control" in {result.header.headers.getOrElse(CACHE_CONTROL, "No-Cache-Control-Header-Set") shouldBe "no-cache" }
    }

    "there is no PSO list stored in the AmendProtectionModel" should {

      lazy val result = await(TestAmendsController.amendPsoDetails("ip2014", "open")(fakeRequest))
      lazy val jsoupDoc = Jsoup.parse(bodyOf(result))

      "return 200" in {
        mockAuthRetrieval[Option[String]](Retrievals.nino, Some("AB123456A"))
        keystoreFetchCondition[AmendProtectionModel](Some(AmendProtectionModel(testProtectionNoPsoList, testProtectionNoPsoList)))
        status(result) shouldBe 200
      }

      "show the amend PSO details page with no data completed" in {
        jsoupDoc.body.getElementsByTag("h1").text shouldEqual Messages("pla.psoDetails.title")
        jsoupDoc.body.getElementById("psoDay").attr("value") shouldEqual ""
        jsoupDoc.body.getElementById("psoMonth").attr("value") shouldEqual ""
        jsoupDoc.body.getElementById("psoYear").attr("value") shouldEqual ""
      }
    }

    "there is an empty PSO list stored in the AmendProtectionModel" should {

      lazy val result = await(TestAmendsController.amendPsoDetails("ip2016", "open")(fakeRequest))
      lazy val jsoupDoc = Jsoup.parse(bodyOf(result))

      "return 200" in {
        mockAuthRetrieval[Option[String]](Retrievals.nino, Some("AB123456A"))
        keystoreFetchCondition[AmendProtectionModel](Some(AmendProtectionModel(testProtectionEmptyPsoList, testProtectionEmptyPsoList)))
        status(result) shouldBe 200
      }

      "show the amend PSO details page with no data completed" in {
        jsoupDoc.body.getElementsByTag("h1").text shouldEqual Messages("pla.psoDetails.title")
        jsoupDoc.body.getElementById("psoDay").attr("value") shouldEqual ""
        jsoupDoc.body.getElementById("psoMonth").attr("value") shouldEqual ""
        jsoupDoc.body.getElementById("psoYear").attr("value") shouldEqual ""
      }
    }

    "there is a PSO list of one PSO stored in the AmendProtectionModel" should {

      object DataItem extends AuthorisedFakeRequestTo(TestAmendsController.amendPsoDetails("ip2016", "open"))

      "return 200" in {
        mockAuthRetrieval[Option[String]](Retrievals.nino, Some("AB123456A"))
        keystoreFetchCondition[AmendProtectionModel](Some(AmendProtectionModel(testProtectionSinglePsoList, testProtectionSinglePsoList)))
        status(DataItem.result) shouldBe 200
      }

      "show the amend PSO details page with the correct data completed" in {
        DataItem.jsoupDoc.body.getElementsByTag("h1").text shouldEqual Messages("pla.psoDetails.title")
        DataItem.jsoupDoc.body.getElementById("psoDay").attr("value") shouldEqual "23"
        DataItem.jsoupDoc.body.getElementById("psoMonth").attr("value") shouldEqual "12"
        DataItem.jsoupDoc.body.getElementById("psoYear").attr("value") shouldEqual "2016"
        DataItem.jsoupDoc.body.getElementById("psoAmt").attr("value") shouldEqual "1000"
      }
    }

    "there is a PSO list of more then one PSO stored in the AmendProtectionModel" should {

      lazy val result = await(TestAmendsController.amendPsoDetails("ip2016", "open")(fakeRequest))
      lazy val jsoupDoc = Jsoup.parse(bodyOf(result))

      "return 200" in {
        mockAuthRetrieval[Option[String]](Retrievals.nino, Some("AB123456A"))
        keystoreFetchCondition[AmendProtectionModel](Some(AmendProtectionModel(testProtectionMultiplePsoList, testProtectionMultiplePsoList)))
        status(result) shouldBe 500
      }
      "show the technical error page for existing protections" in {
        jsoupDoc.body.getElementsByTag("h1").text shouldEqual Messages("pla.techError.pageHeading")
        jsoupDoc.body.getElementById("tryAgainLink").attr("href") shouldEqual s"${controllers.routes.ReadProtectionsController.currentProtections()}"
      }
      "have the correct cache control" in {result.header.headers.getOrElse(CACHE_CONTROL, "No-Cache-Control-Header-Set") shouldBe "no-cache" }
    }
  }

  "Submitting Amend PSOs data" when {

    "submitting valid data for IP14" should {

      object DataItem extends AuthorisedFakeRequestToPost(TestAmendsController.submitAmendPsoDetails,
        ("psoDay", "6"),
        ("psoMonth", "4"),
        ("psoYear", "2014"),
        ("psoAmt", "100000"),
        ("protectionType", "ip2014"),
        ("status", "open"),
        ("existingPSO", "true")
      )

      "return 303" in {
        mockAuthRetrieval[Option[String]](Retrievals.nino, Some("AB123456A"))
        keystoreFetchCondition[AmendProtectionModel](Some(testAmendIP2014ProtectionModel))
        status(DataItem.result) shouldBe 303
      }

      "redirect to the amends summary action for open IP 2014" in {
        redirectLocation(DataItem.result) shouldBe Some(s"${routes.AmendsController.amendsSummary("ip2014", "open")}")
      }
    }

    "submitting valid data for IP16" should {

      object DataItem extends AuthorisedFakeRequestToPost(TestAmendsController.submitAmendPsoDetails,
        ("psoDay", "6"),
        ("psoMonth", "4"),
        ("psoYear", "2016"),
        ("psoAmt", "100000"),
        ("protectionType", "ip2016"),
        ("status", "open"),
        ("existingPSO", "true")
      )

      "return 303" in {
        mockAuthRetrieval[Option[String]](Retrievals.nino, Some("AB123456A"))
        keystoreFetchCondition[AmendProtectionModel](Some(testAmendIP2016ProtectionModel))
        status(DataItem.result) shouldBe 303
      }

      "redirect to the amends summary action for open IP 2016" in {
        redirectLocation(DataItem.result) shouldBe Some(s"${routes.AmendsController.amendsSummary("ip2016", "open")}")
      }
    }

    "submitting invalid data" should {

      object DataItem extends AuthorisedFakeRequestToPost(TestAmendsController.submitAmendPsoDetails,
        ("psoDay", ""),
        ("psoMonth", "1"),
        ("psoYear", "2015"),
        ("psoAmt", "100000"),
        ("protectionType", "ip2014"),
        ("status", "open"),
        ("existingPSO", "true")
      )
      "return 400" in {
        mockAuthRetrieval[Option[String]](Retrievals.nino, Some("AB123456A"))
        status(DataItem.result) shouldBe 400 }
    }

    "submitting data which fails additional validation" should {

      object DataItem extends AuthorisedFakeRequestToPost(TestAmendsController.submitAmendPsoDetails,
        ("psoDay", "36"),
        ("psoMonth", "1"),
        ("psoYear", "2015"),
        ("psoAmt", "100000"),
        ("protectionType", "ip2014"),
        ("status", "open"),
        ("existingPSO", "true")
      )
      "return 400" in {
        mockAuthRetrieval[Option[String]](Retrievals.nino, Some("AB123456A"))
        status(DataItem.result) shouldBe 400 }
    }
  }

  "Removing a recently added PSO" when {

    val testProtectionSinglePsoList = ProtectionModel (
      psaCheckReference = Some("psaRef"),
      protectionID = Some(1234),
      pensionDebits = Some(List(PensionDebitModel("2016-12-23", 1000.0)))
    )

    "there is no amend protection model fetched from keystore" should {
      lazy val result = await(TestAmendsController.removePso("ip2016", "open")(fakeRequest))
      lazy val jsoupDoc = Jsoup.parse(bodyOf(result))

      "return 500" in {
        mockAuthRetrieval[Option[String]](Retrievals.nino, Some("AB123456A"))
        keystoreFetchCondition[AmendProtectionModel](None)
        status(result) shouldBe 500
      }
      "show the technical error page for existing protections" in {
        jsoupDoc.body.getElementsByTag("h1").text shouldEqual Messages("pla.techError.pageHeading")
        jsoupDoc.body.getElementById("tryAgainLink").attr("href") shouldEqual s"${controllers.routes.ReadProtectionsController.currentProtections()}"
      }
      "have the correct cache control" in {result.header.headers.getOrElse(CACHE_CONTROL, "No-Cache-Control-Header-Set") shouldBe "no-cache" }
    }

    "a valid amend protection model is fetched from keystore" should {
      lazy val result = await(TestAmendsController.removePso("ip2016", "open")(fakeRequest))
      lazy val jsoupDoc = Jsoup.parse(bodyOf(result))

      "return 200" in {
        mockAuthRetrieval[Option[String]](Retrievals.nino, Some("AB123456A"))
        keystoreFetchCondition[AmendProtectionModel](Some(AmendProtectionModel(testProtectionSinglePsoList, testProtectionSinglePsoList)))
        status(result) shouldBe 200
      }

      "show the remove pso page with correct details" in {
        jsoupDoc.body.getElementsByTag("h1").text shouldEqual Messages("pla.psoDetails.title")
        jsoupDoc.body.getElementById("protectionType").`val`() shouldEqual "ip2016"
        jsoupDoc.body.getElementById("status").`val`() shouldEqual "open"
      }
    }

    "choosing remove on the remove page" should {
      lazy val result = await(TestAmendsController.submitRemovePso(fakeRequest))
      lazy val jsoupDoc = Jsoup.parse(bodyOf(result))
      "return 400 if the hidden form details were incorrect" in {
        mockAuthRetrieval[Option[String]](Retrievals.nino, Some("AB123456A"))
        status(result) shouldEqual 400
      }

      "return 500 if the an amend protection model could not be retrieved from keystore" in {
        object DataItem extends AuthorisedFakeRequestToPost(TestAmendsController.submitRemovePso,
          ("protectionType", "ip2016"),
          ("status", "open")
        )
        mockAuthRetrieval[Option[String]](Retrievals.nino, Some("AB123456A"))
        keystoreFetchCondition[AmendProtectionModel](None)
        status(DataItem.result) shouldEqual 500
      }

    }

    "Choosing remove with a valid amend protection model" should {
      val ip2016Protection = ProtectionModel(
        psaCheckReference = Some("testPSARef"),
        uncrystallisedRights = Some(100000.00),
        nonUKRights = Some(2000.00),
        preADayPensionInPayment = Some(2000.00),
        postADayBenefitCrystallisationEvents = Some(2000.00),
        notificationId = Some(12),
        protectionID = Some(12345),
        protectionType = Some("IP2016"),
        status = Some("open"),
        certificateDate = Some("2016-04-17"),
        pensionDebits = Some(List(PensionDebitModel("2016-12-23", 1000.0))),
        protectedAmount = Some(1250000),
        protectionReference = Some("PSA123456"))

      val testAmendIP2016ProtectionModel = AmendProtectionModel(ip2016Protection, ip2016Protection)
      object DataItem extends AuthorisedFakeRequestToPost(TestAmendsController.submitRemovePso, ("protectionType", "ip2016"), ("status", "open"))


      "return 303" in {
        mockAuthRetrieval[Option[String]](Retrievals.nino, Some("AB123456A"))
        keystoreFetchCondition[AmendProtectionModel](Some(testAmendIP2016ProtectionModel))
        status(DataItem.result) shouldBe 303
      }

      "redirect location should be the amends summary page" in {
        redirectLocation(DataItem.result) shouldBe Some(s"${routes.AmendsController.amendsSummary("ip2016", "open")}")
      }
    }

    "choosing cancel on the remove page" should {}
  }

}
