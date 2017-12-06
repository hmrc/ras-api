package uk.gov.hmrc.rasapi.services

import uk.gov.hmrc.http.cache.client.SessionCache
import uk.gov.hmrc.rasapi.config.RasSessionCache

trait RasFileOutputService {

  val sessionCache: SessionCache

  def outputResults(envelopeId: String, results: List[String]): Unit = {
    sessionCache.cache[List[String]](envelopeId, results)
  }
}

object RasFileOutputService extends RasFileOutputService {
  override val sessionCache: SessionCache = RasSessionCache
}
