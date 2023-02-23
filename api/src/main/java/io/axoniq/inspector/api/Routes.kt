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
