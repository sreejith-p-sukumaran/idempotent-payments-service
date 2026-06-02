package com.sreejith.payments.service

import com.sreejith.payments.domain.IdempotencyStatus
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
            // Someone already claimed this key: read their row and decide.
            return resolveExisting(key)
        }

        // We own the key. Do the work, then persist its response (phase C).
        val response = work()
        transactions.complete(key, response, Instant.now(clock))
        return IdempotencyOutcome.Processed(response)
    }

    private fun resolveExisting(key: String): IdempotencyOutcome {
        // The row could be swept by expiry between the failed insert and this
        // read; if so, treat it as a transient conflict and let the client retry.
        val record = transactions.find(key) ?: return IdempotencyOutcome.Conflict

        return when (record.status) {
            IdempotencyStatus.COMPLETED ->
                record.storedResponse()
                    ?.let { IdempotencyOutcome.Replayed(it) }
                    ?: IdempotencyOutcome.Conflict
            IdempotencyStatus.IN_PROGRESS -> IdempotencyOutcome.Conflict
        }
    }
}
