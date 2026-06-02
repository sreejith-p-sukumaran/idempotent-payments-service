package com.sreejith.payments.domain

/**
 * Lifecycle state of a payment.
 *
 * Phase 1 only ever produces [SUCCEEDED] — the happy path. Richer states
 * (e.g. PENDING, FAILED) can be added when the business logic grows.
 */
enum class PaymentStatus {
    SUCCEEDED,
}
