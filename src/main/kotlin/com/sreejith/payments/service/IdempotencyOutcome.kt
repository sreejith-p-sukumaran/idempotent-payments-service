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
     * processed concurrently. Maps to 409. (Phase 5 adds a separate outcome for
     * "same key, different request body".)
     */
    data object Conflict : IdempotencyOutcome
}
