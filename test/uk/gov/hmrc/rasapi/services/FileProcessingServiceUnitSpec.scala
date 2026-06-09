/*
 * Copyright 2026 HM Revenue & Customs
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

package uk.gov.hmrc.rasapi.services

import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.{any, eq as Meq}
import org.mockito.Mockito.*
import org.scalatest.BeforeAndAfter
import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongo.cache.CacheItem
import uk.gov.hmrc.rasapi.config.AppContext
import uk.gov.hmrc.rasapi.connectors.{DesConnector, UpscanConnector}
import uk.gov.hmrc.rasapi.helpers.ResidencyYearResolver
import uk.gov.hmrc.rasapi.metrics.Metrics
import uk.gov.hmrc.rasapi.models.*
import uk.gov.hmrc.rasapi.repository.RasFilesRepository

import java.time.{Instant, LocalDate}
import scala.concurrent.ExecutionContext.global
import scala.concurrent.Future
import scala.util.Try

class FileProcessingServiceUnitSpec
    extends AnyWordSpecLike
    with Matchers
    with GuiceOneAppPerSuite
    with MockitoSugar
    with BeforeAndAfter
    with Eventually {

  implicit override val patienceConfig: PatienceConfig =
    PatienceConfig(timeout = Span(5, Seconds), interval = Span(500, Millis))

  given hc: HeaderCarrier                            = HeaderCarrier()
  given fakeReq: FakeRequest[AnyContentAsEmpty.type] = FakeRequest("POST", "/residency-status")

  val appContext: AppContext = app.injector.instanceOf[AppContext]
  val metrics: Metrics       = app.injector.instanceOf[Metrics]

  def buildService(
    upscan: UpscanConnector = mock[UpscanConnector],
    des: DesConnector = mock[DesConnector],
    resolver: ResidencyYearResolver = mock[ResidencyYearResolver],
    audit: AuditService = mock[AuditService],
    sessionCache: RasFilesSessionService = mock[RasFilesSessionService],
    fileRepo: RasFilesRepository = mock[RasFilesRepository]
  ): FileProcessingService = new FileProcessingService(
    upscan,
    des,
    resolver,
    audit,
    sessionCache,
    fileRepo,
    appContext,
    metrics
  )(using global)

  val callbackData: UpscanCallbackData = UpscanCallbackData(
    reference = "ref-1",
    downloadUrl = Some("url"),
    fileStatus = "READY",
    uploadDetails = None,
    failureDetails = None
  )

  val callbackWithoutUrl: UpscanCallbackData = callbackData.copy(downloadUrl = None)

  val cacheItem: CacheItem = CacheItem(
    "sessionValue",
    Json.toJson(Map("user1234" -> Json.toJson(callbackData))).as[JsObject],
    Instant.now,
    Instant.now
  )

  "getCurrentDate" should {
    "default to today's date when not overridden" in {
      val svc = buildService()
      val now = LocalDate.now()
      val gap = math.abs(svc.getCurrentDate.toEpochDay - now.toEpochDay)
      gap should be <= 1L
    }
  }

  "processFile" should {

    "return false and not attempt to read when callbackData has no download URL" in {
      val upscan       = mock[UpscanConnector]
      val sessionCache = mock[RasFilesSessionService]
      val svc          = buildService(upscan = upscan, sessionCache = sessionCache)

      val result = svc.processFile("user1234", callbackWithoutUrl, V2_0)

      result shouldBe false
      verify(upscan, never()).getUpscanFile(any(), any(), any())
    }

    "return true and skip manipulation when readFile fails" in {
      val upscan       = mock[UpscanConnector]
      val sessionCache = mock[RasFilesSessionService]
      when(upscan.getUpscanFile(any(), any(), any())).thenReturn(Future.successful(None))

      val svc    = buildService(upscan = upscan, sessionCache = sessionCache)
      val result = svc.processFile("user1234", callbackData, V2_0)

      result shouldBe true

      // Future not mapped, so eventually should capture when done
      eventually {
        verify(upscan, atLeastOnce()).getUpscanFile(any(), any(), any())
      }
      verify(sessionCache, never()).updateFileSession(any(), any(), any(), any())
    }
  }

  "manipulateFile" should {

    "skip empty rows but still process the file and mark session as FAILED when saveFile fails" in {
      val sessionCache = mock[RasFilesSessionService]
      val fileRepo     = mock[RasFilesRepository]
      val resolver     = mock[ResidencyYearResolver]

      when(fileRepo.saveFile(any(), any(), any())).thenReturn(Future.failed(new RuntimeException("disk full")))
      when(sessionCache.updateFileSession(any(), any(), any(), any())).thenReturn(Future.successful(cacheItem))
      when(resolver.isBetweenJanAndApril).thenReturn(false)

      val svc = buildService(
        resolver = resolver,
        sessionCache = sessionCache,
        fileRepo = fileRepo
      )

      val inputFileData = Try(Iterator("", "  "))
      svc.manipulateFile(inputFileData, "user1234", callbackData, V2_0)

      // Future not mapped, so eventually should capture when done
      eventually {
        val captor: ArgumentCaptor[UpscanCallbackData] = ArgumentCaptor.forClass(classOf[UpscanCallbackData])
        verify(sessionCache, atLeastOnce()).updateFileSession(any(), captor.capture(), any(), any())
        captor.getAllValues.toArray.map(_.asInstanceOf[UpscanCallbackData].fileStatus) should contain("FAILED")
      }
    }
  }

  "saveFile" should {

    "mark the session as FAILED when fileRepo.saveFile fails" in {
      val sessionCache = mock[RasFilesSessionService]
      val fileRepo     = mock[RasFilesRepository]

      when(fileRepo.saveFile(any(), any(), any())).thenReturn(Future.failed(new RuntimeException("disk full")))
      when(sessionCache.updateFileSession(any(), any(), any(), any())).thenReturn(Future.successful(cacheItem))

      val svc = buildService(sessionCache = sessionCache, fileRepo = fileRepo)

      svc.saveFile(java.nio.file.Paths.get("/tmp/does-not-need-to-exist.csv"), "user1234", callbackData)

      // Future not mapped, so eventually should capture when done
      eventually {
        val captor: ArgumentCaptor[UpscanCallbackData] = ArgumentCaptor.forClass(classOf[UpscanCallbackData])
        verify(sessionCache).updateFileSession(
          Meq("user1234"),
          captor.capture(),
          Meq(None),
          Meq(None)
        )
        captor.getValue.fileStatus shouldBe "FAILED"
      }
    }
  }

}
