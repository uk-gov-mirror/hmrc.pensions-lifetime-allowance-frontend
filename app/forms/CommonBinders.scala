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

package forms

import play.api.data.{FormError, Forms, Mapping}
import play.api.data.format.Formatter

import scala.util.{Failure, Success, Try}

trait CommonBinders {

  private val EMPTY_STRING = ""

  private def intFormatter(errorLabel: String) = new Formatter[Int] {

    override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], Int] = {
      data.getOrElse(key, EMPTY_STRING) match {
        case EMPTY_STRING => Left(Seq(FormError(key, s"pla.base.errors.$errorLabel")))
        case str => Try(str.toInt) match {
          case Success(result) => Right(result)
          case Failure(_) => Left(Seq(FormError(key, "error.real")))
        }
      }
    }

    override def unbind(key: String, value: Int): Map[String, String] = Map(key -> value.toString)
  }

  def intWithCustomError(errorLabel: String): Mapping[Int] = Forms.of[Int](intFormatter(errorLabel))
}
