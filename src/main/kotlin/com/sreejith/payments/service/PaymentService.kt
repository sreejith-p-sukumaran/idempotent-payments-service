package com.sreejith.payments.service

import com.sreejith.payments.domain.CreatePaymentCommand
import com.sreejith.payments.domain.Payment
import com.sreejith.payments.domain.PaymentStatus
import com.sreejith.payments.repository.PaymentRepository
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Instant
import java.util.UUID

/**
 * Creates payments. Phase 1 is the happy path only — no idempotency yet; every
 * call persists a brand-new payment. Idempotency is layered on in later phases.
 */
@Service
class PaymentService(
    private val paymentRepository: PaymentRepository,
    private val clock: Clock,
) {

    fun create(command: CreatePaymentCommand): Payment {
        val payment = Payment(
            id = UUID.randomUUID(),
            amount = command.amount,
            currency = command.currency,
            status = PaymentStatus.SUCCEEDED,
            createdAt = Instant.now(clock),
        )
        return paymentRepository.save(payment)
    }
}
