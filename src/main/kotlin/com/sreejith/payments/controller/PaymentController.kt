package com.sreejith.payments.controller

import com.sreejith.payments.controller.dto.CreatePaymentRequest
import com.sreejith.payments.controller.dto.PaymentResponse
import com.sreejith.payments.service.PaymentService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/payments")
class PaymentController(
    private val paymentService: PaymentService,
) {

    @PostMapping
    fun create(@Valid @RequestBody request: CreatePaymentRequest): ResponseEntity<PaymentResponse> {
        val payment = paymentService.create(request.toCommand())
        return ResponseEntity.status(HttpStatus.CREATED).body(PaymentResponse.from(payment))
    }
}
