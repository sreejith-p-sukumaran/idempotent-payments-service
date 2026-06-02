package com.sreejith.payments.repository

import com.sreejith.payments.TestcontainersConfiguration
import com.sreejith.payments.domain.IdempotencyRecord
import com.sreejith.payments.domain.IdempotencyStatus
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.dao.DataIntegrityViolationException
import java.time.Instant

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration::class)
class IdempotencyRecordRepositoryTest {

    @Autowired
    lateinit var records: IdempotencyRecordRepository

    private val now = Instant.parse("2026-01-01T00:00:00Z")

    @Test
    fun `persists an in-progress record and completes it`() {
        records.saveAndFlush(
            IdempotencyRecord(key = "key-1", requestHash = "hash-1", createdAt = now, updatedAt = now),
        )

        val claimed = records.findById("key-1").orElseThrow()
        assertThat(claimed.status).isEqualTo(IdempotencyStatus.IN_PROGRESS)
        assertThat(claimed.storedResponse()).isNull()

        claimed.markCompleted(201, """{"ok":true}""", now.plusSeconds(1))
        records.saveAndFlush(claimed)

        val completed = records.findById("key-1").orElseThrow()
        assertThat(completed.status).isEqualTo(IdempotencyStatus.COMPLETED)
        assertThat(completed.responseStatus).isEqualTo(201)
        assertThat(completed.responseBody).isEqualTo("""{"ok":true}""")
        assertThat(completed.updatedAt).isEqualTo(now.plusSeconds(1))
    }

    @Test
    fun `deleteExpired removes only records created before the cutoff`() {
        records.saveAndFlush(
            IdempotencyRecord(key = "old", requestHash = "h", createdAt = now.minusSeconds(3_600)),
        )
        records.saveAndFlush(
            IdempotencyRecord(key = "fresh", requestHash = "h", createdAt = now),
        )

        val deleted = records.deleteExpired(now.minusSeconds(60))

        assertThat(deleted).isEqualTo(1)
        assertThat(records.findById("old")).isEmpty
        assertThat(records.findById("fresh")).isPresent
    }

    @Test
    fun `rejects a second record with the same key (the insert-wins lock)`() {
        records.saveAndFlush(
            IdempotencyRecord(key = "dup", requestHash = "hash-a", createdAt = now, updatedAt = now),
        )

        assertThatThrownBy {
            records.saveAndFlush(
                IdempotencyRecord(key = "dup", requestHash = "hash-b", createdAt = now, updatedAt = now),
            )
        }.isInstanceOf(DataIntegrityViolationException::class.java)
    }
}
