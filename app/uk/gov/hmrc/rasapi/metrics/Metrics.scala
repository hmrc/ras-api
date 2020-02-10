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

package uk.gov.hmrc.rasapi.metrics

import com.codahale.metrics.{MetricRegistry, Timer}
import uk.gov.hmrc.play.graphite.MicroserviceMetrics

trait Metrics {val responseTimer : Timer}

object Metrics extends Metrics with MicroserviceMetrics {
  val registry: MetricRegistry = metrics.defaultRegistry
  override val responseTimer = registry.timer("ras-api-success")
  def register(name:String) = registry.timer(name)

}
