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

package uk.gov.hmrc.rasapi.connectors

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import com.github.tomakehurst.wiremock.client.WireMock.{
  aResponse, containing, equalTo, equalToJson, post, postRequestedFor, urlEqualTo
}
import com.github.tomakehurst.wiremock.http.Fault
import com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.Mockito.{atLeastOnce, reset, verify}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.{Application, Logger}
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsValue, Json, OFormat}
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import uk.gov.hmrc.http.*
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.play.bootstrap.http.HttpClientV2Provider
import uk.gov.hmrc.play.bootstrap.tools.LogCapturing
import uk.gov.hmrc.rasapi.config.AppContext
import uk.gov.hmrc.rasapi.models.*
import uk.gov.hmrc.rasapi.services.AuditService
import uk.gov.hmrc.rasapi.utils.{CorrelationIdGenerator, WireMockHelper}

import java.net.ServerSocket
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import scala.concurrent.ExecutionContext
import scala.jdk.CollectionConverters.*
import scala.util.Using

class DesConnectorSpec
    extends AnyWordSpecLike
    with Matchers
    with GuiceOneAppPerSuite
    with MockitoSugar
    with WireMockHelper
    with Eventually
    with BeforeAndAfterEach
    with LogCapturing {

  override def fakeApplication(): Application =
    new GuiceApplicationBuilder()
      .configure(
        "play.ws.timeout.request" -> "1s",
        "play.ws.timeout.idle"    -> "1s"
      )
      .build()

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockAuditService)
  }

  given hc: HeaderCarrier = HeaderCarrier()

  given successFormat: OFormat[ResidencyStatusSuccess] = ResidencyStatusFormats.successFormats
  given failureFormat: OFormat[ResidencyStatusFailure] = ResidencyStatusFormats.failureFormats

  val mockAuditService: AuditService = mock[AuditService]

  val servicesConfig: ServicesConfig      = app.injector.instanceOf[ServicesConfig]
  val appContext: AppContext              = app.injector.instanceOf[AppContext]
  lazy val realHttp: HttpClientV2Provider = app.injector.instanceOf[HttpClientV2Provider]

  val uuid   = "123e4567-e89b-42d3-a456-556642440000"
  val userId = "A123456"
  val desUrl = "/individuals/residency-status/"

  val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

  val fixedCorrelationIdGenerator: CorrelationIdGenerator = new CorrelationIdGenerator {
    override def generateNewUUID: String = uuid
  }

  lazy val defaultConnector: DesConnector = createDesConnector()
  lazy val desLogger: Logger              = Logger(defaultConnector.getClass)

  def createDesConnector(
    allowNoNextYearStatusEnabled: Boolean = true,
    retryEnabled: Boolean = true,
    bulkRetryEnabled: Boolean = false,
    baseUrl: String = s"http://localhost:${server.port()}"
  ): DesConnector =
    new DesConnector(realHttp, mockAuditService, appContext, fixedCorrelationIdGenerator)(using
      ExecutionContext.global
    ) {
      override lazy val desBaseUrl: String             = baseUrl
      override lazy val allowNoNextYearStatus: Boolean = allowNoNextYearStatusEnabled
      override lazy val retryLimit: Int                = 3
      override lazy val retryDelay: Int                = 500
      override lazy val desUrlHeaderEnv: String        = "DES HEADER"
      override lazy val desAuthToken: String           = "DES AUTH TOKEN"
      override lazy val isRetryEnabled: Boolean        = retryEnabled
      override lazy val isBulkRetryEnabled: Boolean    = bulkRetryEnabled
    }

  val member: IndividualDetails =
    IndividualDetails("AB123456C", "JOHN", "SMITH", LocalDate.parse("1990-02-21", formatter))

  val noStatusReason = "Cannot provide a residency status for this pension scheme member."

  val internalServerError: ResidencyStatusFailure =
    ResidencyStatusFailure("INTERNAL_SERVER_ERROR", "Internal server error.")

  def successBody(
    current: String = "Uk",
    nextYear: Option[String] = Some("Scottish"),
    deceased: Option[Boolean] = Some(false)
  ): String =
    Json
      .toJson(
        ResidencyStatusSuccess(
          nino = member.nino,
          deathDate = Some(""),
          deathDateStatus = Some(""),
          deseasedIndicator = deceased,
          currentYearResidencyStatus = current,
          nextYearResidencyStatus = nextYear
        )
      )
      .toString()

  def failureBody(failure: ResidencyStatusFailure): String = Json.toJson(failure).toString()

  def stubDesResponse(status: Int, body: String = ""): Unit =
    server.stubFor(
      post(urlEqualTo(desUrl))
        .willReturn(
          aResponse()
            .withStatus(status)
            .withHeader("Content-Type", "application/json")
            .withBody(body)
        )
    )

  // first call returns 429, every subsequent call returns 200 with the given body
  def stubDes429ThenSuccess(scenarioName: String, body: String): Unit = {
    server.stubFor(
      post(urlEqualTo(desUrl))
        .inScenario(scenarioName)
        .whenScenarioStateIs(STARTED)
        .willReturn(aResponse().withStatus(429).withHeader("Content-Type", "application/json"))
        .willSetStateTo("Second Request")
    )
    server.stubFor(
      post(urlEqualTo(desUrl))
        .inScenario(scenarioName)
        .whenScenarioStateIs("Second Request")
        .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(body))
    )
  }

  def verifyDesPost(expectedCount: Int = 1): Unit = {
    val expectedDesPayload: JsValue = Json.obj(
      "nino"                        -> member.nino,
      "firstName"                   -> member.firstName,
      "lastName"                    -> member.lastName,
      "dob"                         -> member.dateOfBirth.format(formatter),
      "pensionSchemeOrganisationID" -> userId
    )

    server.verify(
      expectedCount,
      postRequestedFor(urlEqualTo(desUrl))
        .withHeader("Environment", equalTo("DES HEADER"))
        .withHeader("OriginatorId", equalTo("DA_RAS"))
        .withHeader("Authorization", equalTo("Bearer DES AUTH TOKEN"))
        .withHeader("Content-Type", containing("application/json"))
        .withHeader("CorrelationId", equalTo(uuid))
        .withRequestBody(equalToJson(expectedDesPayload.toString()))
    )
  }

  def desAudits(): List[Map[String, String]] = {
    val captor: ArgumentCaptor[Map[String, String]] = ArgumentCaptor.forClass(classOf[Map[String, String]])

    verify(mockAuditService, atLeastOnce())
      .audit(eqTo(defaultConnector.DES_AUDIT_TYPE), any[String], captor.capture())(using any[HeaderCarrier])

    captor.getAllValues.asScala.toList
  }

  def baseAuditFields(attempt: Int, outcome: String, requestSource: String = "API"): Map[String, String] = Map(
    "downstreamService" -> "DES",
    "userIdentifier"    -> userId,
    "nino"              -> member.nino,
    "correlationId"     -> uuid,
    "requestSource"     -> requestSource,
    "attempt"           -> attempt.toString,
    "outcome"           -> outcome
  )

  def assertSingleFailureAudit(audits: List[Map[String, String]], fields: (String, String)*): Unit = {
    audits      should have size 1
    audits.head should contain allElementsOf baseAuditFields(attempt = 1, outcome = "FAILURE") ++ fields
  }

  def assertFailureAuditPerAttempt(
    audits: List[Map[String, String]],
    attempts: Int,
    fields: (String, String)*
  ): Unit = {
    audits.map(_("attempt")) shouldBe (1 to attempts).map(_.toString).toList
    audits.foreach(_ should contain allElementsOf Map("outcome" -> "FAILURE", "correlationId" -> uuid) ++ fields)
  }

  def messages(logs: List[ILoggingEvent]): List[String] = logs.map(_.getFormattedMessage)

  def messagesAt(level: Level, logs: List[ILoggingEvent]): List[String] =
    logs.filter(_.getLevel == level).map(_.getFormattedMessage)

  val tag = "[DesConnector][getResidencyStatus]"

  "DESConnector getResidencyStatus" should {

    "return the residency status, and audit SUCCESS with the DES status, when 200 is returned with CY and CYPlusOne" in
      withCaptureOfLoggingFrom(desLogger) { logs =>
        stubDesResponse(200, successBody())

        await(defaultConnector.getResidencyStatus(member, userId)) shouldBe
          Right(ResidencyStatus("otherUKResident", Some("scotResident")))

        verifyDesPost()

        val audits = desAudits()
        audits      should have size 1
        audits.head should contain allElementsOf baseAuditFields(attempt = 1, outcome = "SUCCESS") +
          ("httpStatus" -> "200")

        // success is visible through the audit only, to keep bulk-file log volume down
        logs shouldBe empty
      }

    "When the requested API version is V2.0" should {

      "map a Welsh resident for CY and CYPlusOne" in {
        stubDesResponse(200, successBody(current = "Welsh", nextYear = Some("Welsh")))

        await(defaultConnector.getResidencyStatus(member, userId)) shouldBe
          Right(ResidencyStatus("welshResident", Some("welshResident")))

        verifyDesPost()
      }

      "map a Welsh resident moving to Scotland" in {
        stubDesResponse(200, successBody(current = "Welsh", nextYear = Some("Scottish")))

        await(defaultConnector.getResidencyStatus(member, userId)) shouldBe
          Right(ResidencyStatus("welshResident", Some("scotResident")))

        verifyDesPost()
      }
    }

    "return only CY when CYPlusOne is missing and the no-next-year-status toggle is on" in {
      stubDesResponse(200, successBody(nextYear = None, deceased = None))

      await(defaultConnector.getResidencyStatus(member, userId)) shouldBe
        Right(ResidencyStatus("otherUKResident", None))

      verifyDesPost()
    }

    "return internal server error, and audit MISSING_NEXT_YEAR_STATUS, when CYPlusOne is missing and the toggle is off" in {
      val connector = createDesConnector(allowNoNextYearStatusEnabled = false)
      stubDesResponse(200, successBody(nextYear = None))

      await(connector.getResidencyStatus(member, userId)) shouldBe
        Left(ResidencyStatusFailure(connector.error_InternalServerError, "Internal server error."))

      verifyDesPost()

      val audits       = desAudits()
      assertSingleFailureAudit(
        audits,
        "reason"       -> "MISSING_NEXT_YEAR_STATUS",
        "httpStatus"   -> "200",
        "responseCode" -> "DO_NOT_RE_PROCESS"
      )
    }

    "return matching failed, and audit MATCHING_FAILED with status 404, when DES returns 404" in {
      stubDesResponse(404, failureBody(ResidencyStatusFailure("STATUS_UNAVAILABLE", noStatusReason)))

      await(defaultConnector.getResidencyStatus(member, userId)) shouldBe
        Left(ResidencyStatusFailure(appContext.matchingFailedStatus, noStatusReason))

      verifyDesPost()

      val audits       = desAudits()
      assertSingleFailureAudit(
        audits,
        "reason"       -> "MATCHING_FAILED",
        "httpStatus"   -> "404",
        "responseCode" -> appContext.matchingFailedStatus
      )
    }

    "return matching failed, and audit MATCHING_FAILED with status 403, when DES returns 403" in {
      stubDesResponse(403, """{"code":"STATUS_UNAVAILABLE"}""")

      await(defaultConnector.getResidencyStatus(member, userId)) shouldBe
        Left(ResidencyStatusFailure(appContext.matchingFailedStatus, noStatusReason))

      verifyDesPost()

      val audits       = desAudits()
      assertSingleFailureAudit(
        audits,
        "reason"       -> "MATCHING_FAILED",
        "httpStatus"   -> "403",
        "responseCode" -> appContext.matchingFailedStatus
      )
    }

    "return deceased, and audit DECEASED, when DES flags the member as deceased" in
      withCaptureOfLoggingFrom(desLogger) { logs =>
        stubDesResponse(200, successBody(nextYear = Some("Uk"), deceased = Some(true)))

        await(defaultConnector.getResidencyStatus(member, userId)) shouldBe
          Left(ResidencyStatusFailure("DECEASED", noStatusReason))

        verifyDesPost()

        val audits       = desAudits()
        assertSingleFailureAudit(
          audits,
          "reason"       -> "DECEASED",
          "httpStatus"   -> "200",
          "responseCode" -> "DECEASED"
        )

        logs.map(event => event.getLevel -> event.getFormattedMessage) shouldBe List(
          Level.INFO ->
            (s"$tag outcome=FAILURE reason=DECEASED userId=$userId corrId=$uuid " +
              "attempt=1 responseCode=DECEASED status=200")
        )
      }

    "return internal server error, and audit BAD_REQUEST, when DES returns 400" in {
      stubDesResponse(400, failureBody(ResidencyStatusFailure("DO_NOT_RE_PROCESS", "Internal server error.")))

      await(defaultConnector.getResidencyStatus(member, userId)) shouldBe Left(internalServerError)

      verifyDesPost()

      val audits = desAudits()

      assertSingleFailureAudit(
        audits,
        "reason"       -> "BAD_REQUEST",
        "httpStatus"   -> "400",
        "responseCode" -> "DO_NOT_RE_PROCESS"
      )
    }

    "retry, then return internal server error and audit each attempt, when DES returns 500" in
      withCaptureOfLoggingFrom(desLogger) { logs =>
        stubDesResponse(500, failureBody(internalServerError))

        await(defaultConnector.getResidencyStatus(member, userId)) shouldBe Left(internalServerError)

        verifyDesPost(expectedCount = 3)

        val audits     = desAudits()
        assertFailureAuditPerAttempt(
          audits,
          attempts = 3,
          "reason"     -> "INTERNAL_SERVER_ERROR",
          "httpStatus" -> "500"
        )

        // unexpected failures log at ERROR, once per attempt
        val errorLogs = messagesAt(Level.ERROR, logs)

        errorLogs should have size 3

        errorLogs.zipWithIndex.foreach { (line, i) =>
          line should include("outcome=FAILURE reason=INTERNAL_SERVER_ERROR")
          line should include(s"attempt=${i + 1}")
          line should include("status=500")
        }
      }

    "retry, then return service unavailable and audit each attempt, when DES returns 503" in {
      stubDesResponse(503)

      await(defaultConnector.getResidencyStatus(member, userId)) shouldBe
        Left(ResidencyStatusFailure("SERVICE_UNAVAILABLE", "Service unavailable"))

      verifyDesPost(expectedCount = 3)

      val audits     = desAudits()
      assertFailureAuditPerAttempt(
        audits,
        attempts = 3,
        "reason"     -> "SERVICE_UNAVAILABLE",
        "httpStatus" -> "503"
      )
    }

    "retry, then return internal server error and audit UNEXPECTED_STATUS, when DES returns a status we don't handle" in {
      stubDesResponse(502)

      await(defaultConnector.getResidencyStatus(member, userId)) shouldBe Left(internalServerError)

      verifyDesPost(expectedCount = 3)

      val audits = desAudits()

      assertFailureAuditPerAttempt(
        audits,
        attempts = 3,
        "reason"       -> "UNEXPECTED_STATUS",
        "httpStatus"   -> "502",
        "responseCode" -> "INTERNAL_SERVER_ERROR"
      )
    }

    "return internal server error, and audit TIMEOUT with no HTTP status, when the DES request times out" in {
      server.stubFor(
        post(urlEqualTo(desUrl)).willReturn(aResponse().withStatus(200).withFixedDelay(4000))
      )

      await(defaultConnector.getResidencyStatus(member, userId)) shouldBe Left(internalServerError)

      eventually(timeout(Span(8, Seconds)), interval(Span(200, Millis))) {
        verifyDesPost()
      }

      val audits = desAudits()

      assertSingleFailureAudit(
        audits,
        "reason"       -> "TIMEOUT",
        "cause"        -> "GatewayTimeoutException",
        "responseCode" -> "DO_NOT_RE_PROCESS"
      )
      audits.head.keySet should not contain "httpStatus"
    }

    "return internal server error, and audit the failure, when DES resets the connection" in {
      val connector = createDesConnector(retryEnabled = false)
      server.stubFor(
        post(urlEqualTo(desUrl)).willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER))
      )

      await(connector.getResidencyStatus(member, userId)) shouldBe Left(internalServerError)

      verifyDesPost()

      val audits = desAudits()

      assertSingleFailureAudit(
        audits,
        "reason"       -> "INTERNAL_SERVER_ERROR",
        "responseCode" -> "INTERNAL_SERVER_ERROR"
      )
      audits.head.keySet should contain("cause")
    }

    "return internal server error, and audit CONNECTION_FAILURE, when DES refuses the connection" in {

      // a local port with nothing listening, so connecting to it is refused
      val unusedPort: Int = Using.resource(new ServerSocket(0))(_.getLocalPort)

      val connector = createDesConnector(retryEnabled = false, baseUrl = s"http://localhost:$unusedPort")

      await(connector.getResidencyStatus(member, userId)) shouldBe Left(internalServerError)

      val audits = desAudits()

      assertSingleFailureAudit(
        audits,
        "reason"       -> "CONNECTION_FAILURE",
        "cause"        -> "BadGatewayException",
        "responseCode" -> "INTERNAL_SERVER_ERROR"
      )

      audits.head.keySet should not contain "httpStatus"
    }

    "retry, then return internal server error and audit PARSE_ERROR without parse paths, when DES returns non-JSON" in
      withCaptureOfLoggingFrom(desLogger) { logs =>
        stubDesResponse(200, s"<html>${member.nino}</html>")

        await(defaultConnector.getResidencyStatus(member, userId)) shouldBe Left(internalServerError)

        verifyDesPost(expectedCount = 3)

        val audits = desAudits()
        assertFailureAuditPerAttempt(audits, attempts = 3, "reason" -> "PARSE_ERROR", "httpStatus" -> "200")
        audits.foreach { audit =>
          audit.keySet should contain("cause")
          audit.keySet should not contain "parseErrors"
        }

        // the parser's message quotes the body; logs carry only the exception type
        val errorLogs = messagesAt(Level.ERROR, logs)

        errorLogs should have size 3
        errorLogs.foreach(_ should include("outcome=FAILURE reason=PARSE_ERROR"))
        errorLogs.foreach(_ should include("cause="))
        messages(logs).foreach(_ should not include member.nino)
        messages(logs).foreach(_ should not include "<html>")
      }

    "retry, then return internal server error and audit INVALID_RESPONSE with the failing JSON paths, " +
      "when DES returns JSON of the wrong shape" in
      withCaptureOfLoggingFrom(desLogger) { logs =>
        stubDesResponse(200, s"""{"nino":"${member.nino}","unexpected":"shape"}""")

        await(defaultConnector.getResidencyStatus(member, userId)) shouldBe Left(internalServerError)

        verifyDesPost(expectedCount = 3)

        val audits = desAudits()
        assertFailureAuditPerAttempt(audits, attempts = 3, "reason" -> "INVALID_RESPONSE", "httpStatus" -> "200")
        audits.foreach(_("parseErrors") should include("/currentYearResidencyStatus"))

        // logs name the failing JSON paths, never values in the body
        val errorLogs = messagesAt(Level.ERROR, logs)

        errorLogs should have size 3
        errorLogs.foreach(_ should include("outcome=FAILURE reason=INVALID_RESPONSE"))
        errorLogs.foreach(_ should include("parseErrors=/currentYearResidencyStatus"))
        messages(logs).foreach(_ should not include member.nino)
      }

    "pass DES's failure body through, and audit DES_FAILURE_RESPONSE, when DES returns a failure body with 200" in {
      val desFailure = ResidencyStatusFailure("STATUS_UNAVAILABLE", noStatusReason)
      stubDesResponse(200, failureBody(desFailure))

      await(defaultConnector.getResidencyStatus(member, userId)) shouldBe Left(desFailure)

      verifyDesPost()

      val audits = desAudits()

      assertSingleFailureAudit(
        audits,
        "reason"       -> "DES_FAILURE_RESPONSE",
        "httpStatus"   -> "200",
        "responseCode" -> "STATUS_UNAVAILABLE"
      )
    }

    "for a bulk request" should {

      "retry a 429 when bulk retry is on but retry is off, auditing both attempts as FE_BULK" in {
        val connector = createDesConnector(retryEnabled = false, bulkRetryEnabled = true)
        stubDes429ThenSuccess("bulk-retry-429-then-success", successBody())

        await(connector.getResidencyStatus(member, userId, isBulkRequest = true)) shouldBe
          Right(ResidencyStatus("otherUKResident", Some("scotResident")))

        verifyDesPost(expectedCount = 2)

        val audits = desAudits()
        audits should have size 2

        audits.head should contain allElementsOf baseAuditFields(1, "FAILURE", "FE_BULK") ++ Map(
          "reason"     -> "TOO_MANY_REQUESTS",
          "httpStatus" -> "429"
        )

        audits(1) should contain allElementsOf baseAuditFields(2, "SUCCESS", "FE_BULK") + ("httpStatus" -> "200")
      }

      "not retry a 429 when both bulk retry and retry are off" in {
        val connector = createDesConnector(retryEnabled = false, bulkRetryEnabled = false)
        stubDesResponse(429)

        await(connector.getResidencyStatus(member, userId, isBulkRequest = true)) shouldBe
          Left(ResidencyStatusFailure("TOO_MANY_REQUESTS", "Too Many Requests."))

        verifyDesPost()

        val audits = desAudits()
        audits should have size 1

        audits.head should contain allElementsOf baseAuditFields(
          1,
          "FAILURE",
          "FE_BULK"
        ) + ("reason" -> "TOO_MANY_REQUESTS")
      }
    }

    "retrying" should {

      "succeed on the second attempt after a 429, auditing a FAILURE then a SUCCESS under one correlation id" in {
        stubDes429ThenSuccess("retry-429-then-success", successBody(nextYear = None))

        await(defaultConnector.getResidencyStatus(member, userId)) shouldBe
          Right(ResidencyStatus("otherUKResident"))

        verifyDesPost(expectedCount = 2)

        val audits = desAudits()
        audits.map(a => a("attempt") -> a("outcome")) shouldBe List("1" -> "FAILURE", "2" -> "SUCCESS")
        audits.map(_("correlationId")).distinct       shouldBe List(uuid)
      }

      "give up after 3 attempts when DES keeps returning 429, auditing every attempt" in
        withCaptureOfLoggingFrom(desLogger) { logs =>
          stubDesResponse(429)

          await(defaultConnector.getResidencyStatus(member, userId)) shouldBe
            Left(ResidencyStatusFailure("TOO_MANY_REQUESTS", "Too Many Requests."))

          verifyDesPost(expectedCount = 3)

          val audits       = desAudits()
          assertFailureAuditPerAttempt(
            audits,
            attempts = 3,
            "reason"       -> "TOO_MANY_REQUESTS",
            "httpStatus"   -> "429",
            "responseCode" -> "TOO_MANY_REQUESTS"
          )

          // throttling logs at WARN - a failure line per attempt, and a retry line before attempts 2 and 3
          val warningLogs = messagesAt(Level.WARN, logs)
          warningLogs.filter(_.contains("outcome=FAILURE reason=TOO_MANY_REQUESTS")) should have size 3

          warningLogs.filter(_.contains("Retrying DES call")) shouldBe List(
            s"$tag Retrying DES call userId=$userId corrId=$uuid attempt=2",
            s"$tag Retrying DES call userId=$userId corrId=$uuid attempt=3"
          )

          messagesAt(Level.ERROR, logs) shouldBe empty
        }
    }
  }

}
