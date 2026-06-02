package com.sreejith.payments.domain

/**
 * State of an idempotency record.
 *
 * - [IN_PROGRESS]: the key has been claimed and the work is running (or the
 *   process died mid-flight). Blocks duplicates.
 * - [COMPLETED]: the work finished and the response was captured for replay.
 */
enum class IdempotencyStatus {
    IN_PROGRESS,
    COMPLETED,
}
