package com.sreejith.payments.controller

import com.sreejith.payments.controller.dto.CreatePaymentRequest
import com.sreejith.payments.domain.StoredResponse
import com.sreejith.payments.service.IdempotencyOutcome
import com.sreejith.payments.service.PaymentApplicationService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/payments")
class PaymentController(
    private val paymentApplicationService: PaymentApplicationService,
) {

    @PostMapping
    fun create(
        @RequestHeader(IDEMPOTENCY_KEY_HEADER) idempotencyKey: String,
        @Valid @RequestBody request: CreatePaymentRequest,
    ): ResponseEntity<String> {
        val outcome = paymentApplicationService.createPayment(idempotencyKey, request.toCommand())
        return when (outcome) {
            is IdempotencyOutcome.Processed -> jsonResponse(outcome.response, replayed = false)
            is IdempotencyOutcome.Replayed -> jsonResponse(outcome.response, replayed = true)
            IdempotencyOutcome.Conflict -> ResponseEntity.status(HttpStatus.CONFLICT).build()
        }
    }

    /**
     * Writes a stored response back verbatim (status + JSON body). On a replay
     * we add an `Idempotent-Replayed` header so callers can observe it — the
     * status code and body remain byte-identical to the original.
     */
    private fun jsonResponse(stored: StoredResponse, replayed: Boolean): ResponseEntity<String> {
        val builder = ResponseEntity.status(stored.httpStatus).contentType(MediaType.APPLICATION_JSON)
        if (replayed) {
            builder.header(IDEMPOTENT_REPLAYED_HEADER, "true")
        }
        return builder.body(stored.body)
    }

    companion object {
        const val IDEMPOTENCY_KEY_HEADER = "Idempotency-Key"
        const val IDEMPOTENT_REPLAYED_HEADER = "Idempotent-Replayed"
    }
}
