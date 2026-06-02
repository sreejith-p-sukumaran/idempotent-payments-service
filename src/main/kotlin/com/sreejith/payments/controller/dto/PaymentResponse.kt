package com.sreejith.payments.controller.dto

import com.sreejith.payments.domain.Payment
import com.sreejith.payments.domain.PaymentStatus
import java.time.Instant
import java.util.UUID

/**
 * Outgoing JSON body representing a created payment.
 */
data class PaymentResponse(
    val id: UUID,
    val amount: Long,
    val currency: String,
    val status: PaymentStatus,
    val createdAt: Instant,
) {
    companion object {
        fun from(payment: Payment): PaymentResponse =
            PaymentResponse(
                id = payment.id,
                amount = payment.amount,
                currency = payment.currency,
                status = payment.status,
                createdAt = payment.createdAt,
            )
    }
}
