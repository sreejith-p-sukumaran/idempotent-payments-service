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
 * The key property: on a claim collision the work lambda must NOT run.
 */
class IdempotencyServiceTest {

    private val fixedClock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC)
    private val transactions = mockk<IdempotencyTransactions>()
    private val service = IdempotencyService(transactions, fixedClock)

    @Test
    fun `claims the key, runs the work, then records the response`() {
        every { transactions.claim("key-1", "hash-1", any()) } returns mockk<IdempotencyRecord>()
        every { transactions.complete("key-1", any(), any()) } returns Unit
        var workRan = false

        val outcome = service.execute("key-1", "hash-1") {
            workRan = true
            StoredResponse(201, """{"ok":true}""")
        }

        assertThat(workRan).isTrue()
        assertThat(outcome).isEqualTo(IdempotencyOutcome.Processed(StoredResponse(201, """{"ok":true}""")))
        verify(exactly = 1) {
            transactions.complete("key-1", StoredResponse(201, """{"ok":true}"""), Instant.parse("2026-01-01T00:00:00Z"))
        }
    }

    @Test
    fun `does not run the work or complete when the key is already claimed`() {
        every { transactions.claim(any(), any(), any()) } throws DataIntegrityViolationException("duplicate key")
        var workRan = false

        val outcome = service.execute("key-1", "hash-1") {
            workRan = true
            StoredResponse(201, """{"ok":true}""")
        }

        assertThat(workRan).isFalse()
        assertThat(outcome).isEqualTo(IdempotencyOutcome.Conflict)
        verify(exactly = 0) { transactions.complete(any(), any(), any()) }
    }
}
