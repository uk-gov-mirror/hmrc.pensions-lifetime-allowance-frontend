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

package enums

import play.api.libs.json.{JsString, Json}
import enums.IdentityVerificationResult.IdentityVerificationResult
import uk.gov.hmrc.play.test.UnitSpec

class IdentityVerificationResultSpec extends UnitSpec {
  "IdentityVerificationResult" when {
    "serialising to JSON" should {
      "return JSString" in {
        Json.toJson(IdentityVerificationResult.Success) shouldBe JsString("Success")
      }
    }

    "deserialising from JSON" should {
      "return IdentityVerificationResult.Success" in {
        JsString("Success").as[IdentityVerificationResult] shouldBe IdentityVerificationResult.Success
      }
    }
  }
}
