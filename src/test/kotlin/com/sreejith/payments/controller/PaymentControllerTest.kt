package com.sreejith.payments.controller

import com.ninjasquad.springmockk.MockkBean
import com.sreejith.payments.domain.CreatePaymentCommand
import com.sreejith.payments.domain.StoredResponse
import com.sreejith.payments.service.IdempotencyOutcome
import com.sreejith.payments.service.PaymentApplicationService
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

/**
 * Web slice: only the controller + Spring MVC. The application service is a
 * MockK mock, so these tests pin down HTTP concerns — header handling,
 * validation, and mapping an [IdempotencyOutcome] to a response.
 */
@WebMvcTest(PaymentController::class)
class PaymentControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockkBean
    lateinit var paymentApplicationService: PaymentApplicationService

    private val storedBody =
        """{"id":"11111111-1111-1111-1111-111111111111","amount":1000,"currency":"EUR","status":"SUCCEEDED","createdAt":"2026-01-01T00:00:00Z"}"""

    @Test
    fun `returns 201 with the stored body for a processed request`() {
        val commandSlot = slot<CreatePaymentCommand>()
        every {
            paymentApplicationService.createPayment("key-1", capture(commandSlot))
        } returns IdempotencyOutcome.Processed(StoredResponse(201, storedBody))

        mockMvc.post("/payments") {
            headers { set("Idempotency-Key", "key-1") }
            contentType = MediaType.APPLICATION_JSON
            content = """{"amount":1000,"currency":"eur"}"""
        }.andExpect {
            status { isCreated() }
            content { contentType(MediaType.APPLICATION_JSON) }
            jsonPath("$.id") { value("11111111-1111-1111-1111-111111111111") }
            jsonPath("$.amount") { value(1000) }
            jsonPath("$.currency") { value("EUR") }
        }

        assertThat(commandSlot.captured.currency).isEqualTo("EUR")
        assertThat(commandSlot.captured.amount).isEqualTo(1000)
    }

    @Test
    fun `replays the stored body with the Idempotent-Replayed header`() {
        every {
            paymentApplicationService.createPayment("key-1", any())
        } returns IdempotencyOutcome.Replayed(StoredResponse(201, storedBody))

        mockMvc.post("/payments") {
            headers { set("Idempotency-Key", "key-1") }
            contentType = MediaType.APPLICATION_JSON
            content = """{"amount":1000,"currency":"EUR"}"""
        }.andExpect {
            status { isCreated() }
            header { string("Idempotent-Replayed", "true") }
            jsonPath("$.id") { value("11111111-1111-1111-1111-111111111111") }
        }
    }

    @Test
    fun `does not set the Idempotent-Replayed header on a fresh response`() {
        every {
            paymentApplicationService.createPayment(any(), any())
        } returns IdempotencyOutcome.Processed(StoredResponse(201, storedBody))

        mockMvc.post("/payments") {
            headers { set("Idempotency-Key", "key-1") }
            contentType = MediaType.APPLICATION_JSON
            content = """{"amount":1000,"currency":"EUR"}"""
        }.andExpect {
            status { isCreated() }
            header { doesNotExist("Idempotent-Replayed") }
        }
    }

    @Test
    fun `returns a deliberate 409 problem with Retry-After for an in-flight duplicate`() {
        every { paymentApplicationService.createPayment(any(), any()) } returns IdempotencyOutcome.Conflict

        mockMvc.post("/payments") {
            headers { set("Idempotency-Key", "key-1") }
            contentType = MediaType.APPLICATION_JSON
            content = """{"amount":1000,"currency":"EUR"}"""
        }.andExpect {
            status { isConflict() }
            header { string("Retry-After", "1") }
            content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.code") { value("IN_PROGRESS") }
        }
    }

    @Test
    fun `returns 422 when the key was reused with a different body`() {
        every { paymentApplicationService.createPayment(any(), any()) } returns IdempotencyOutcome.Mismatch

        mockMvc.post("/payments") {
            headers { set("Idempotency-Key", "key-1") }
            contentType = MediaType.APPLICATION_JSON
            content = """{"amount":1000,"currency":"EUR"}"""
        }.andExpect {
            status { isUnprocessableEntity() }
            content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.code") { value("IDEMPOTENCY_KEY_MISMATCH") }
        }
    }

    @Test
    fun `returns 400 when the Idempotency-Key header is missing`() {
        mockMvc.post("/payments") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"amount":1000,"currency":"EUR"}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("MISSING_IDEMPOTENCY_KEY") }
        }

        verify(exactly = 0) { paymentApplicationService.createPayment(any(), any()) }
    }

    @Test
    fun `returns 400 when the Idempotency-Key header is blank`() {
        mockMvc.post("/payments") {
            headers { set("Idempotency-Key", "   ") }
            contentType = MediaType.APPLICATION_JSON
            content = """{"amount":1000,"currency":"EUR"}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("MISSING_IDEMPOTENCY_KEY") }
        }

        verify(exactly = 0) { paymentApplicationService.createPayment(any(), any()) }
    }

    @Test
    fun `returns 400 with field errors for a non-positive amount and never calls the service`() {
        mockMvc.post("/payments") {
            headers { set("Idempotency-Key", "key-1") }
            contentType = MediaType.APPLICATION_JSON
            content = """{"amount":0,"currency":"EUR"}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("VALIDATION_FAILED") }
            jsonPath("$.errors.amount") { exists() }
        }

        verify(exactly = 0) { paymentApplicationService.createPayment(any(), any()) }
    }

    @Test
    fun `returns 400 for a malformed currency`() {
        mockMvc.post("/payments") {
            headers { set("Idempotency-Key", "key-1") }
            contentType = MediaType.APPLICATION_JSON
            content = """{"amount":1000,"currency":"EURO"}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("VALIDATION_FAILED") }
            jsonPath("$.errors.currency") { exists() }
        }

        verify(exactly = 0) { paymentApplicationService.createPayment(any(), any()) }
    }
}
