package com.sreejith.payments.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.PostLoad
import jakarta.persistence.PostPersist
import jakarta.persistence.Table
import jakarta.persistence.Transient
import org.springframework.data.domain.Persistable
import java.time.Instant

/**
 * Tracks one idempotent operation, keyed by the client-supplied idempotency key.
 *
 * Implements [Persistable] so Spring Data always issues an `INSERT` (never a
 * `merge`) for a fresh record. That matters: with an assigned (non-generated)
 * primary key, `save()` would otherwise treat the entity as detached and `merge`
 * it — silently overwriting an existing row instead of failing. We rely on the
 * INSERT failing with a unique-constraint violation to detect a duplicate key.
 */
@Entity
@Table(name = "idempotency_record")
class IdempotencyRecord(
    @Id
    @Column(name = "idempotency_key", length = 255)
    val key: String,

    @Column(name = "request_hash", nullable = false, length = 64)
    val requestHash: String,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: IdempotencyStatus = IdempotencyStatus.IN_PROGRESS,

    @Column(name = "response_status")
    var responseStatus: Int? = null,

    @Column(name = "response_body")
    var responseBody: String? = null,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = createdAt,
) : Persistable<String> {

    @Transient
    private var persisted: Boolean = false

    override fun getId(): String = key

    override fun isNew(): Boolean = !persisted

    @PostLoad
    @PostPersist
    fun markPersisted() {
        persisted = true
    }

    /** Transition from IN_PROGRESS to COMPLETED, capturing the response to replay. */
    fun markCompleted(httpStatus: Int, body: String, at: Instant) {
        status = IdempotencyStatus.COMPLETED
        responseStatus = httpStatus
        responseBody = body
        updatedAt = at
    }

    fun storedResponse(): StoredResponse? {
        val httpStatus = responseStatus ?: return null
        val body = responseBody ?: return null
        return StoredResponse(httpStatus, body)
    }
}
