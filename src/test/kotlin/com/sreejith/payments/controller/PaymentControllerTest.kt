package com.sreejith.payments.controller

import com.ninjasquad.springmockk.MockkBean
import com.sreejith.payments.domain.CreatePaymentCommand
import com.sreejith.payments.domain.Payment
import com.sreejith.payments.domain.PaymentStatus
import com.sreejith.payments.service.PaymentService
import io.mockk.every
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import java.time.Instant
import java.util.UUID

/**
 * Web slice test: only the controller + Spring MVC are loaded; the service is
 * a MockK mock. Verifies request validation, mapping, and the 201 response.
 */
@WebMvcTest(PaymentController::class)
class PaymentControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockkBean
    lateinit var paymentService: PaymentService

    @Test
    fun `creates a payment and returns 201 with the stored representation`() {
        val id = UUID.randomUUID()
        val commandSlot = slot<CreatePaymentCommand>()
        every { paymentService.create(capture(commandSlot)) } returns Payment(
            id = id,
            amount = 1_000,
            currency = "EUR",
            status = PaymentStatus.SUCCEEDED,
            createdAt = Instant.parse("2026-01-01T00:00:00Z"),
        )

        mockMvc.post("/payments") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"amount":1000,"currency":"eur"}"""
        }.andExpect {
            status { isCreated() }
            jsonPath("$.id") { value(id.toString()) }
            jsonPath("$.amount") { value(1_000) }
            jsonPath("$.currency") { value("EUR") }
            jsonPath("$.status") { value("SUCCEEDED") }
        }

        // currency is normalised to upper-case before reaching the service
        assertThat(commandSlot.captured.currency).isEqualTo("EUR")
    }

    @Test
    fun `rejects a non-positive amount with 400 and never calls the service`() {
        mockMvc.post("/payments") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"amount":0,"currency":"EUR"}"""
        }.andExpect {
            status { isBadRequest() }
        }

        verify(exactly = 0) { paymentService.create(any()) }
    }

    @Test
    fun `rejects a malformed currency with 400`() {
        mockMvc.post("/payments") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"amount":1000,"currency":"EURO"}"""
        }.andExpect {
            status { isBadRequest() }
        }

        verify(exactly = 0) { paymentService.create(any()) }
    }
}
