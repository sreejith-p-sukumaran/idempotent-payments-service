package com.sreejith.payments.service

import com.sreejith.payments.domain.StoredResponse
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Instant

/**
 * Runs an operation at most once per idempotency key.
 *
 * Crucially **not** `@Transactional`: it orchestrates two independent
 * transactions ([IdempotencyTransactions.claim] then
 * [IdempotencyTransactions.complete]) with the business [work] running in
 * between, after the claim has committed. See DESIGN.md §2.
 */
@Service
class IdempotencyService(
    private val transactions: IdempotencyTransactions,
    private val clock: Clock,
) {

    fun execute(key: String, requestHash: String, work: () -> StoredResponse): IdempotencyOutcome {
        try {
            transactions.claim(key, requestHash, Instant.now(clock))
        } catch (_: DataIntegrityViolationException) {
            // Someone already claimed this key. Phase 2 reports a flat conflict;
            // later phases read the existing row and decide replay vs 409 vs 422.
            return IdempotencyOutcome.Conflict
        }

        // We own the key. Do the work, then persist its response (phase C).
        val response = work()
        transactions.complete(key, response, Instant.now(clock))
        return IdempotencyOutcome.Processed(response)
    }
}
