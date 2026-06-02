package com.sreejith.payments.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * A payment record. Immutable once created: every field is a `val`, and the
 * `kotlin-jpa` plugin synthesises the no-arg constructor Hibernate needs.
 *
 * Amounts are stored in the currency's minor unit (e.g. cents) as a [Long] to
 * avoid floating-point rounding errors.
 */
@Entity
@Table(name = "payment")
class Payment(
    @Id
    val id: UUID,

    @Column(nullable = false)
    val amount: Long,

    @Column(nullable = false, length = 3)
    val currency: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val status: PaymentStatus,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,
)
