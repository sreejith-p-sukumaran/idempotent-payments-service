package com.sreejith.payments.domain

/**
 * Validated, web-agnostic instruction to create a payment. Keeps HTTP/JSON
 * concerns out of the service layer — the controller maps its request DTO into
 * this command before calling the service.
 */
data class CreatePaymentCommand(
    val amount: Long,
    val currency: String,
)
