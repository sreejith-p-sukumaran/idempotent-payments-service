package com.sreejith.payments.service

import com.sreejith.payments.domain.IdempotencyRecord
import com.sreejith.payments.domain.StoredResponse
import com.sreejith.payments.repository.IdempotencyRecordRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * The two transactional halves of an idempotent operation, kept in their own
 * bean so each runs in a *separate* committed transaction.
 *
 * This separation is the crux of the design (DESIGN.md §2): [claim] must commit
 * before the business work runs, so the IN_PROGRESS row becomes visible to
 * concurrent requests under READ COMMITTED. Wrapping claim + work + complete in
 * one transaction would hide the row until the very end, and concurrent
 * requests would each think they were first.
 *
 * These methods are deliberately called through the Spring proxy from
 * [IdempotencyService] (a different bean) — calling them via `this` would skip
 * the proxy and the separate-transaction guarantee.
 */
@Component
class IdempotencyTransactions(
    private val records: IdempotencyRecordRepository,
) {

    /**
     * Phase A: claim the key by inserting an IN_PROGRESS row and committing.
     *
     * `saveAndFlush` forces the INSERT to hit the database inside this
     * transaction, so a duplicate key fails fast with a
     * `DataIntegrityViolationException` the caller can branch on.
     */
    @Transactional
    fun claim(key: String, requestHash: String, now: Instant): IdempotencyRecord {
        val record = IdempotencyRecord(
            key = key,
            requestHash = requestHash,
            createdAt = now,
            updatedAt = now,
        )
        return records.saveAndFlush(record)
    }

    /**
     * Phase C: record the captured response and flip the row to COMPLETED, in
     * its own transaction.
     */
    @Transactional
    fun complete(key: String, response: StoredResponse, now: Instant) {
        val record = records.findById(key).orElseThrow {
            IllegalStateException("idempotency record '$key' disappeared before completion")
        }
        record.markCompleted(response.httpStatus, response.body, now)
        records.saveAndFlush(record)
    }
}
