/*
 * Copyright 2015-2016 IBM Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package whisk.common

import scala.annotation.implicitNotFound
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration
import scala.language.postfixOps

import akka.actor.ActorSystem

import spray.json.JsValue
import spray.json.pimpAny
import spray.json.DefaultJsonProtocol.StringJsonFormat

/**
 * An object with an underlying thread that periodically stores into the ConsulKV store.
 *
 * Set up a Consul KV interface at the given agent address.
 */
class ConsulKVReporter(
    kv: ConsulClient,
    initialDelay: FiniteDuration,
    interval: FiniteDuration,
    hostKey: String,
    startKey: String,
    statusKey: String,
    updater: () => Map[String, JsValue])(
        implicit val system: ActorSystem,
        val ec: ExecutionContext) {

    system.scheduler.scheduleOnce(initialDelay) {
        val (selfHostname, stderr, exitCode) = SimpleExec.syncRunCmd(Array("hostname", "-f"))(TransactionId.unknown)
        kv.put(hostKey, selfHostname.toJson.compactPrint)
        kv.put(startKey, DateUtil.getTimeString.toJson.compactPrint)

        system.scheduler.schedule(0 seconds, interval) {
            kv.put(statusKey, DateUtil.getTimeString.toJson.compactPrint)
            updater() foreach {
                case (k, v) => kv.put(k, v.compactPrint)
            }
        }
    }
}
