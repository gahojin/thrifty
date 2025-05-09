/*
 * Thrifty
 *
 * Copyright (c) Microsoft Corporation
 * Copyright (c) GAHOJIN, Inc.
 *
 * All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the License);
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * THIS CODE IS PROVIDED ON AN  *AS IS* BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, EITHER EXPRESS OR IMPLIED, INCLUDING
 * WITHOUT LIMITATION ANY IMPLIED WARRANTIES OR CONDITIONS OF TITLE,
 * FITNESS FOR A PARTICULAR PURPOSE, MERCHANTABLITY OR NON-INFRINGEMENT.
 *
 * See the Apache Version 2.0 License for specific language governing permissions and limitations under the License.
 */
package jp.co.gahojin.thrifty.service.server

import jp.co.gahojin.thrifty.ThriftException
import jp.co.gahojin.thrifty.protocol.MessageMetadata
import jp.co.gahojin.thrifty.protocol.Protocol
import jp.co.gahojin.thrifty.service.TMessageType

interface ErrorHandler {
    suspend fun onError(e: Throwable, msg: MessageMetadata, input: Protocol, output: Protocol, oneWay: Boolean)
}

object DefaultErrorHandler : ErrorHandler {
    override suspend fun onError(
        e: Throwable,
        msg: MessageMetadata,
        input: Protocol,
        output: Protocol,
        oneWay: Boolean,
    ) {
        if (!oneWay) {
            msg.reply(output, TMessageType.EXCEPTION) {
                val err = ThriftException(ThriftException.Kind.INTERNAL_ERROR, "Internal error processing ${msg.name}")
                err.write(output)
            }
        }
    }
}
