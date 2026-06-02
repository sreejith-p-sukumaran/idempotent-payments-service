package com.sreejith.payments.controller

import com.sreejith.payments.controller.dto.CreatePaymentRequest
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
            is IdempotencyOutcome.Processed ->
                ResponseEntity.status(outcome.response.httpStatus)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(outcome.response.body)

            IdempotencyOutcome.Conflict ->
                ResponseEntity.status(HttpStatus.CONFLICT).build()
        }
    }

    companion object {
        const val IDEMPOTENCY_KEY_HEADER = "Idempotency-Key"
    }
}
