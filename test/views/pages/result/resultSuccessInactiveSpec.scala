/*
 * Copyright 2018 HM Revenue & Customs
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

package views.pages.result

import enums.ApplicationType
import models.{ProtectionDetailsDisplayModel, RejectionDisplayModel, SuccessDisplayModel}
import play.api.i18n.Messages.Implicits._
import org.jsoup.Jsoup
import play.api.i18n.Messages
import testHelpers.ViewSpecHelpers.{CommonMessages, CommonViewSpecHelper}
import testHelpers.ViewSpecHelpers.result.resultSuccessInactive
import views.html.pages.result.{resultSuccessInactive => views}

class resultSuccessInactiveSpec extends CommonViewSpecHelper with CommonMessages with resultSuccessInactive {

  "The Result Rejected Page" should {

    lazy val protectionmodel = ProtectionDetailsDisplayModel(Some(""), "", Some(""))
    lazy val testmodel = SuccessDisplayModel(ApplicationType.IP2016, "24", "100.00", false, Some(protectionmodel), Seq("1", "2"))
    implicit lazy val view = views(testmodel)
    implicit lazy val doc = Jsoup.parse(view.body)

    "have the correct title" in {
      doc.title() shouldBe plaResultSuccessTitle
    }

    "have the first heading which" should {

      lazy val h1Tag = doc.select("h1")

      "have the heading text" in {
        h1Tag.text shouldBe "You've added fixed protection 2016"
      }

      "have the correct Id" in {
        h1Tag.attr("id") shouldBe "resultOutcome"
      }
    }

    "have a result code paragraph which" should {

      "have the text" in {
        doc.body.select("p").get(0).text  shouldBe "As you already have individual protection 2014 in place, fixed protection 2016 will only become active if you lose individual protection 2014."//Messages(s"$resultCode."+res.notificationId+".$infoNum")
      }

      "have the correct Id" in {
        doc.body.select("p").get(0).attr("id") shouldBe "additionalInfo1"
      }
    }

    "have a secondary heading which" should {

      lazy val h2Tag0 = doc.select("h2").get(0)

      "have the heading text" in {
        h2Tag0.text shouldBe plaResultSuccessIPChangeDetails
      }

    }

    "have a IP Pension sharing paragraph which" should {

      "have the text" in {
        doc.select("p").get(2).text shouldBe plaResultSuccessIPPensionSharing
      }

      "have the correct Id" in {
        doc.select("p").get(2).attr("id") shouldBe "ipPensionSharing"
      }
    }

      /*If Application Type = FP2016 ...*/
//
//    "have a FP Add To Pension paragraph which" should {
//
//      "have the text" in {
//        doc.select("p").get(1).text shouldBe plaResultSuccessFPAddToPension
//      }
//
//      "have the correct Id" in {
//        doc.select("p").get(1).attr("id") shouldBe "fpAddToPension"
//      }
//    }

    "have a Existing Protections paragraph which" should {

      lazy val detailsLink = doc.select("p a").get(2)

      "have the text" in {
        doc.select("p").get(3).text shouldBe s"$plaResultRejectionViewDetails $plaResultRejectionViewDetailsLinkText."
      }

      "have the link text" in {
        detailsLink.text shouldBe plaResultRejectionViewDetailsLinkText
      }

      "have the link id" in {
        detailsLink.attr("id") shouldBe "existingProtectionsLink"
      }

      "have the link destination" in {
        detailsLink.attr("href") shouldBe "/protect-your-lifetime-allowance/existing-protections"
      }

    }

    "have a third heading which" should {

      lazy val h2Tag1 = doc.select("h2").get(1)

      "have the heading text" in {
        h2Tag1.text shouldBe plaResultSuccessGiveFeedback
      }

    }

    "have a Exit Survey paragraph which" should {

      lazy val exitLink = doc.select("p a").get(3)

      "have the text" in {
        doc.select("p").get(4).text shouldBe s"$plaResultSuccessExitSurveyLinkText $plaResultSuccessExitSurvey"
      }

      "have the link text" in {
        exitLink.text shouldBe plaResultSuccessExitSurveyLinkText
      }

      "have the link destination" in {
        exitLink.attr("href") shouldBe "/protect-your-lifetime-allowance/exit"
      }

    }

  }
}

//@views.html.main_template(title = Messages("pla.resultSuccess.title"), bodyClasses = None) {
//
//<h1 id="resultOutcome">@Messages(s"resultCode.${res.notificationId}.heading")</h1>
//
//@for(infoNum <- res.additionalInfo) {
//<p id=@{s"additionalInfo$infoNum"}>@Html(Messages(s"resultCode.${res.notificationId}.$infoNum"))</p>
//}
//
//
//
//<h2>@Messages("pla.resultSuccess.IPChangeDetails")</h2>
//
//@if(res.protectionType == ApplicationType.IP2016 || res.protectionType == ApplicationType.IP2014 || Constants.fpShowPensionSharing.contains(res.notificationId.toInt)) {
//<p id="ipPensionSharing">@Html(Messages("pla.resultSuccess.IPPensionSharing"))</p>
//}
//
//@if(res.protectionType == ApplicationType.FP2016 || Constants.ipShowAddToPension.contains(res.notificationId.toInt)) {
//<p id="fpAddToPension">@Html(Messages("pla.resultSuccess.FPAddToPension"))</p>
//}
//
//<p>@Html(Messages("pla.resultRejection.viewDetails")) <a id="existingProtectionsLink" href=@controllers.routes.ReadProtectionsController.currentProtections>@Messages("pla.resultRejection.viewDetailsLinkText")</a>.</p>
//
//
//<h2>@Messages("pla.resultSuccess.giveFeedback")</h2>
//
//<p><a href=@controllers.routes.ExitSurveyController.exitSurvey>@Messages("pla.resultSuccess.exitSurveyLinkText")</a> @Html(Messages("pla.resultSuccess.exitSurvey"))</p>