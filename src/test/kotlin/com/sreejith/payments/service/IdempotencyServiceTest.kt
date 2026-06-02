package com.sreejith.payments.service

import com.sreejith.payments.domain.IdempotencyRecord
import com.sreejith.payments.domain.StoredResponse
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.dao.DataIntegrityViolationException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * Unit test of the orchestration logic with the transactional layer mocked.
 * Key properties: on a collision the work lambda must NOT run, and a COMPLETED
 * row is replayed while an IN_PROGRESS row is reported as a conflict.
 */
class IdempotencyServiceTest {

    private val now = Instant.parse("2026-01-01T00:00:00Z")
    private val fixedClock = Clock.fixed(now, ZoneOffset.UTC)
    private val transactions = mockk<IdempotencyTransactions>()
    private val service = IdempotencyService(transactions, fixedClock)

    private fun work(flag: BooleanArray): () -> StoredResponse = {
        flag[0] = true
        StoredResponse(201, """{"ok":true}""")
    }

    @Test
    fun `claims the key, runs the work, then records the response`() {
        every { transactions.claim("key-1", "hash-1", now) } returns mockk<IdempotencyRecord>()
        every { transactions.complete("key-1", any(), any()) } returns Unit
        val ran = booleanArrayOf(false)

        val outcome = service.execute("key-1", "hash-1", work(ran))

        assertThat(ran[0]).isTrue()
        assertThat(outcome).isEqualTo(IdempotencyOutcome.Processed(StoredResponse(201, """{"ok":true}""")))
        verify(exactly = 1) {
            transactions.complete("key-1", StoredResponse(201, """{"ok":true}"""), now)
        }
    }

    @Test
    fun `replays the stored response when the existing record is completed`() {
        val completed = IdempotencyRecord(key = "key-1", requestHash = "hash-1", createdAt = now)
            .apply { markCompleted(201, """{"stored":true}""", now) }
        every { transactions.claim(any(), any(), any()) } throws DataIntegrityViolationException("dup")
        every { transactions.find("key-1") } returns completed
        val ran = booleanArrayOf(false)

        val outcome = service.execute("key-1", "hash-1", work(ran))

        assertThat(ran[0]).isFalse()
        assertThat(outcome).isEqualTo(IdempotencyOutcome.Replayed(StoredResponse(201, """{"stored":true}""")))
        verify(exactly = 0) { transactions.complete(any(), any(), any()) }
    }

    @Test
    fun `reports a conflict when the existing record is still in progress`() {
        val inProgress = IdempotencyRecord(key = "key-1", requestHash = "hash-1", createdAt = now)
        every { transactions.claim(any(), any(), any()) } throws DataIntegrityViolationException("dup")
        every { transactions.find("key-1") } returns inProgress
        val ran = booleanArrayOf(false)

        val outcome = service.execute("key-1", "hash-1", work(ran))

        assertThat(ran[0]).isFalse()
        assertThat(outcome).isEqualTo(IdempotencyOutcome.Conflict)
        verify(exactly = 0) { transactions.complete(any(), any(), any()) }
    }

    @Test
    fun `reports a mismatch when the same key arrives with a different request body`() {
        val existing = IdempotencyRecord(key = "key-1", requestHash = "hash-1", createdAt = now)
            .apply { markCompleted(201, """{"stored":true}""", now) }
        every { transactions.claim(any(), any(), any()) } throws DataIntegrityViolationException("dup")
        every { transactions.find("key-1") } returns existing
        val ran = booleanArrayOf(false)

        val outcome = service.execute("key-1", "a-different-hash", work(ran))

        assertThat(ran[0]).isFalse()
        assertThat(outcome).isEqualTo(IdempotencyOutcome.Mismatch)
        verify(exactly = 0) { transactions.complete(any(), any(), any()) }
    }

    @Test
    fun `reports a conflict when the record vanished after the failed insert`() {
        every { transactions.claim(any(), any(), any()) } throws DataIntegrityViolationException("dup")
        every { transactions.find("key-1") } returns null

        val outcome = service.execute("key-1", "hash-1") { StoredResponse(201, "{}") }

        assertThat(outcome).isEqualTo(IdempotencyOutcome.Conflict)
    }
}
