/*
 * Copyright 2020 HM Revenue & Customs
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

package uk.gov.hmrc.rasapi.repositories

import org.scalatest.BeforeAndAfter
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.bson.BSONLong
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.rasapi.config.AppContext
import uk.gov.hmrc.rasapi.repository.RasFilesRepository

import scala.concurrent.ExecutionContext.Implicits.global

class GridFsTTLIndexingSpec extends UnitSpec with MockitoSugar with GuiceOneAppPerSuite
  with BeforeAndAfter {
  val mockMongo = app.injector.instanceOf[ReactiveMongoComponent]
  val mockAppContext = app.injector.instanceOf[AppContext]

  val rasFileRepository: RasFilesRepository = new RasFilesRepository (
    mockMongo,
    mockAppContext
  )

  before{
    rasFileRepository.removeAll()
  }

  "GridFsTTLIndexingSpec" should {
  "ensure indexes and create if not available" in {
    val defaultTTL = BSONLong(3600)
    val res =  await(rasFileRepository.gridFSG.files.indexesManager.list())

    res.filter(_.name.get == "lastUpdatedIndex")
      .head.options.get("expireAfterSeconds").get shouldBe defaultTTL
    }
  }
}
