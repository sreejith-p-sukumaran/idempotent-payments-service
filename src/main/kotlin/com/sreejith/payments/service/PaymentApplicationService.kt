package com.sreejith.payments.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.sreejith.payments.controller.dto.PaymentResponse
import com.sreejith.payments.domain.CreatePaymentCommand
import com.sreejith.payments.domain.StoredResponse
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service

/**
 * Use-case layer for creating a payment idempotently. Wires the generic
 * [IdempotencyService] to the payment-specific work: create the payment, then
 * serialize its API representation so the exact response can be stored and
 * replayed later.
 */
@Service
class PaymentApplicationService(
    private val idempotencyService: IdempotencyService,
    private val paymentService: PaymentService,
    private val requestHasher: RequestHasher,
    private val objectMapper: ObjectMapper,
) {

    fun createPayment(idempotencyKey: String, command: CreatePaymentCommand): IdempotencyOutcome {
        val requestHash = requestHasher.hash(command)
        return idempotencyService.execute(idempotencyKey, requestHash) {
            val payment = paymentService.create(command)
            val body = objectMapper.writeValueAsString(PaymentResponse.from(payment))
            StoredResponse(HttpStatus.CREATED.value(), body)
        }
    }
}
