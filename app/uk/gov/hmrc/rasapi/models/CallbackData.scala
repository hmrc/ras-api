package uk.gov.hmrc.rasapi.models

import play.api.libs.json.Json

case class CallbackData(envelopeId: String, fileId: String, status: String, reason: Option[String])

object CallbackData {
  implicit val formats = Json.format[CallbackData]
}