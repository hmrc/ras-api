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

package uk.gov.hmrc.rasapi.models

enum FailureReason {

  case MATCHING_FAILED, DECEASED, MISSING_NEXT_YEAR_STATUS, DES_FAILURE_RESPONSE, TOO_MANY_REQUESTS,
    SERVICE_UNAVAILABLE, TIMEOUT, INVALID_RESPONSE, UNKNOWN_RESIDENCY_STATUS, INTERNAL_SERVER_ERROR, PARSE_ERROR,
    CONNECTION_FAILURE, BAD_REQUEST, UNEXPECTED_STATUS, DOWNLOAD_INCOMPLETE

}
