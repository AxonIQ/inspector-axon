/*
 * Copyright (c) 2022-2023. Inspector Axon
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.axoniq.inspector.api

object Routes {

    object EventProcessor {
        const val REPORT = "processor-info-report"

        const val START = "processor-command-start"
        const val STOP = "processor-command-stop"

        const val RELEASE = "processor-command-release-segment"
        const val SPLIT = "processor-command-split-segment"
        const val MERGE = "processor-command-merge-segment"
        const val RESET = "processor-command-reset"

        const val STATUS = "event-processor-status"
        const val SEGMENTS = "event-processor-segments"
        const val CLAIM = "event-processor-claim"
    }

    object ProcessingGroup {

        object DeadLetter {
            const val LETTERS = "dlq-query-dead-letters"
            const val SEQUENCE_SIZE = "dlq-query-dead-letter-sequence-size"

            const val DELETE_SEQUENCE = "dlq-command-delete-sequence"
            const val DELETE_LETTER = "dlq-command-delete-letter"
            const val PROCESS = "dlq-command-process"
        }
    }

    object MessageFlow {
        const val STATS = "message-flow-stats"
    }
}
