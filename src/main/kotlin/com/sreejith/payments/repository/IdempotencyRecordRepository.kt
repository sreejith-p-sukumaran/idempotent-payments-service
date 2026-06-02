package com.sreejith.payments.repository

import com.sreejith.payments.domain.IdempotencyRecord
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

interface IdempotencyRecordRepository : JpaRepository<IdempotencyRecord, String> {

    /**
     * Bulk-deletes records created before [cutoff] in a single DELETE (not a
     * load-then-delete), so the scheduled sweep scales. `clearAutomatically`
     * drops the now-stale entities from the persistence context afterwards.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM IdempotencyRecord r WHERE r.createdAt < :cutoff")
    fun deleteExpired(@Param("cutoff") cutoff: Instant): Int
}
