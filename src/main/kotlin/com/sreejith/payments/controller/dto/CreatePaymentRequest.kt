package com.sreejith.payments.controller.dto

import com.sreejith.payments.domain.CreatePaymentCommand
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size

/**
 * Incoming JSON body for `POST /payments`. Validated at the controller boundary
 * before being mapped to a [CreatePaymentCommand].
 */
data class CreatePaymentRequest(
    @field:Positive(message = "amount must be a positive number of minor units")
    val amount: Long,

    @field:NotBlank(message = "currency is required")
    @field:Size(min = 3, max = 3, message = "currency must be a 3-letter ISO 4217 code")
    val currency: String,
) {
    fun toCommand(): CreatePaymentCommand =
        CreatePaymentCommand(amount = amount, currency = currency.uppercase())
}
