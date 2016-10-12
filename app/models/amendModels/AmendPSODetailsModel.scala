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

package models.amendModels

import play.api.libs.json._

trait AmendPSODetailsBaseModel {
  def psoDay: Int
  def psoMonth: Int
  def psoYear: Int
  def psoAmt: BigDecimal
}

class AmendPSODetailsModel (val psoDay: Int, val psoMonth: Int, val psoYear: Int, val psoAmt: BigDecimal) extends AmendPSODetailsBaseModel {
}

object AmendPSODetailsModel {
  implicit val format = Json.format[AmendPSODetailsModel]

  def apply(psoDay: Option[Int], psoMonth: Option[Int], psoYear: Option[Int], psoAmt: BigDecimal) =
    new AmendPSODetailsModel(psoDay.getOrElse(0), psoMonth.getOrElse(0), psoYear.getOrElse(0), psoAmt)

  def unapply(details: AmendPSODetailsModel): Option[(Option[Int], Option[Int], Option[Int], BigDecimal)] =
    Some((Some(details.psoDay), Some(details.psoMonth), Some(details.psoYear), details.psoAmt))

}
