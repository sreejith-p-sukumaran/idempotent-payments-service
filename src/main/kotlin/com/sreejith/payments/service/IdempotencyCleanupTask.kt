package com.sreejith.payments.service

import com.sreejith.payments.config.IdempotencyProperties
import com.sreejith.payments.repository.IdempotencyRecordRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant

/**
 * Deletes idempotency records once they pass the retention window.
 *
 * Besides bounding table growth, this is the safety valve for the two-phase
 * commit (DESIGN.md §2): a row left stranded in IN_PROGRESS by a crash between
 * claim and complete keeps blocking duplicates (correct — we don't know if the
 * work happened) until it eventually expires here.
 */
@Component
class IdempotencyCleanupTask(
    private val records: IdempotencyRecordRepository,
    private val properties: IdempotencyProperties,
    private val clock: Clock,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun removeExpired(): Int {
        val cutoff = Instant.now(clock).minus(properties.retention)
        val deleted = records.deleteExpired(cutoff)
        if (deleted > 0) {
            log.info("Expired {} idempotency record(s) created before {}", deleted, cutoff)
        }
        return deleted
    }
}
