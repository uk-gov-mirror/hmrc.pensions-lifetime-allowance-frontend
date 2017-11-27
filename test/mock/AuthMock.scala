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

package mock

import org.mockito.Matchers
import org.mockito.Mockito.when
import org.mockito.stubbing.OngoingStubbing
import org.scalatest.mockito.MockitoSugar
import uk.gov.hmrc.auth.core.PlayAuthConnector
import uk.gov.hmrc.auth.core.retrieve.Retrieval

import scala.concurrent.Future

trait AuthMock {
  this: MockitoSugar =>

  val mockAuthConnector = mock[PlayAuthConnector]

  def mockAuthRetrieval[A](retrieval: Retrieval[A], returnValue: A): OngoingStubbing[Future[A]] = {
    when(mockAuthConnector.authorise[A](Matchers.any(), Matchers.eq(retrieval))(Matchers.any(), Matchers.any()))
      .thenReturn(Future.successful(returnValue))
  }

  def mockAuthConnector[T](future: Future[T]): OngoingStubbing[Future[T]] = {
    when(mockAuthConnector.authorise[T](Matchers.any(), Matchers.any())(Matchers.any(), Matchers.any()))
      .thenReturn(future)
  }

}
