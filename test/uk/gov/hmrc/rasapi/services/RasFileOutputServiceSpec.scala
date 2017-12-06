package uk.gov.hmrc.rasapi.services

import org.scalatest.BeforeAndAfter
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.OneServerPerSuite
import uk.gov.hmrc.http.cache.client.SessionCache
import uk.gov.hmrc.play.test.UnitSpec
import org.mockito.Mockito._
import org.mockito.Matchers._

class RasFileOutputServiceSpec extends UnitSpec with OneServerPerSuite with ScalaFutures with MockitoSugar with BeforeAndAfter {

  val mockSessionCache = mock[SessionCache]
  val SUT = new RasFileOutputService {
    override val sessionCache: SessionCache = mockSessionCache
  }

  "outputResults" should {
    "write each result line by line to the session cache" in {
      val results = List("Nino, firstName, lastName, dob, cyResult, cy+1Result")

      SUT.outputResults("envelopeId", results)

      verify(mockSessionCache.cache[List[String]](any(), any()), times(1))
    }
  }
}
