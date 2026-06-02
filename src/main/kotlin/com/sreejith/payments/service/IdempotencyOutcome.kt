package com.sreejith.payments.service

import com.sreejith.payments.domain.StoredResponse

/**
 * Result of running an operation through [IdempotencyService].
 */
sealed interface IdempotencyOutcome {

    /** This request owned the key and did the work; [response] is fresh. */
    data class Processed(val response: StoredResponse) : IdempotencyOutcome

    /**
     * The key was already taken. Phase 2 treats every collision as a conflict;
     * Phases 3-5 split this into replay (COMPLETED), 409 (IN_PROGRESS), and
     * 422 (same key, different request body).
     */
    data object Conflict : IdempotencyOutcome
}
