package com.sreejith.payments.service

import com.sreejith.payments.TestcontainersConfiguration
import com.sreejith.payments.domain.IdempotencyRecord
import com.sreejith.payments.repository.IdempotencyRecordRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import java.time.Duration
import java.time.Instant

/**
 * Full-context test of the expiry sweep against real Postgres. Uses the default
 * 24h retention; records well outside / inside that window verify the cutoff,
 * including a stranded IN_PROGRESS row.
 */
@SpringBootTest
@Import(TestcontainersConfiguration::class)
class IdempotencyCleanupTaskTest {

    @Autowired
    lateinit var task: IdempotencyCleanupTask

    @Autowired
    lateinit var records: IdempotencyRecordRepository

    @BeforeEach
    fun clean() {
        records.deleteAll()
    }

    @Test
    fun `removes records past the retention window, including stranded in-progress`() {
        val now = Instant.now()
        // Stranded IN_PROGRESS from a crash 48h ago — should be swept.
        records.saveAndFlush(
            IdempotencyRecord(key = "stranded", requestHash = "h", createdAt = now.minus(Duration.ofHours(48))),
        )
        // Completed but old (30h) — past the 24h window.
        records.saveAndFlush(
            IdempotencyRecord(key = "old-done", requestHash = "h", createdAt = now.minus(Duration.ofHours(30)))
                .apply { markCompleted(201, """{"ok":true}""", now.minus(Duration.ofHours(30))) },
        )
        // Recent — still serviceable for replay.
        records.saveAndFlush(
            IdempotencyRecord(key = "fresh", requestHash = "h", createdAt = now.minus(Duration.ofHours(1))),
        )

        val deleted = task.removeExpired()

        assertThat(deleted).isEqualTo(2)
        assertThat(records.findById("stranded")).isEmpty
        assertThat(records.findById("old-done")).isEmpty
        assertThat(records.findById("fresh")).isPresent
    }
}
