package com.sreejith.payments.service

import com.sreejith.payments.domain.StoredResponse

/**
 * Result of running an operation through [IdempotencyService].
 */
sealed interface IdempotencyOutcome {

    /** This request owned the key and did the work; [response] is fresh. */
    data class Processed(val response: StoredResponse) : IdempotencyOutcome

    /**
     * The key already exists and its work had COMPLETED; [response] is the
     * stored response, returned verbatim so the retry is indistinguishable from
     * the original call.
     */
    data class Replayed(val response: StoredResponse) : IdempotencyOutcome

    /**
     * The key exists but its work is still IN_PROGRESS — a duplicate is being
     * processed concurrently. Maps to 409.
     */
    data object Conflict : IdempotencyOutcome

    /**
     * The key exists but was first used with a *different* request body — client
     * misuse of the idempotency key. Maps to 422; we refuse rather than return a
     * result for a different request.
     */
    data object Mismatch : IdempotencyOutcome
}
