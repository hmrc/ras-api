/*
 * Copyright 2025 HM Revenue & Customs
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

import org.mockito.Mockito.when
import org.mongodb.scala.Document
import org.mongodb.scala.bson.{BsonInt32, BsonInt64, BsonString}
import org.scalatest.BeforeAndAfter
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatestplus.mockito.MockitoSugar
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport
import uk.gov.hmrc.rasapi.config.AppContext
import uk.gov.hmrc.rasapi.models.Chunks
import uk.gov.hmrc.rasapi.repositories.RepositoriesHelper.createFile
import uk.gov.hmrc.rasapi.repository.RasFilesRepository

import scala.concurrent.ExecutionContext.Implicits.global

class GridFsTTLIndexingSpec extends AnyWordSpecLike
    with Matchers
    with MockitoSugar
    with BeforeAndAfter
    with DefaultPlayMongoRepositorySupport[Chunks] {

  val mockAppContext: AppContext = mock[AppContext]
  when(mockAppContext.resultsExpriyTime).thenReturn(3600)

  override lazy val repository = new RasFilesRepository(mongoComponent, mockAppContext)
  lazy val LastUpdatedIndex = "lastUpdatedIndex"
  lazy val OptExpireAfterSeconds = "expireAfterSeconds"
  lazy val UploadDate = "uploadDate"

  "GridFsTTLIndexing" should {
    "Add the lastUpdatedIndex with TTL option to a collection" in {
      await(repository.saveFile("user111","reference111",createFile))
      val indexes: Seq[Document] = await(repository.mongoComponent.database.getCollection("resultsFiles.files").listIndexes().toFuture())
      val expectedIndex: Document = Document(
        "v" -> BsonInt32(value = 2),
        "key" -> "uploadDate: 1",
        "name" -> BsonString(LastUpdatedIndex),
        "ns" -> BsonString("test-GridFsTTLIndexingSpec.resultsFiles.files"),
        OptExpireAfterSeconds -> BsonInt64(repository.expireAfterSeconds)
      )
      indexes contains expectedIndex
    }
  }
}
