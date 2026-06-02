package com.sreejith.payments

import com.sreejith.payments.domain.CreatePaymentCommand
import com.sreejith.payments.domain.IdempotencyRecord
import com.sreejith.payments.domain.IdempotencyStatus
import com.sreejith.payments.repository.IdempotencyRecordRepository
import com.sreejith.payments.repository.PaymentRepository
import com.sreejith.payments.service.RequestHasher
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import java.time.Instant

/**
 * End-to-end idempotency behaviour for Phase 2: the first request creates the
 * payment and completes the record; a second request with the same key does NOT
 * create another payment (Phase 2 reports 409 — replay arrives in Phase 3).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
class PaymentIdempotencyIntegrationTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var paymentRepository: PaymentRepository

    @Autowired
    lateinit var idempotencyRecordRepository: IdempotencyRecordRepository

    @Autowired
    lateinit var requestHasher: RequestHasher

    @BeforeEach
    fun clean() {
        paymentRepository.deleteAll()
        idempotencyRecordRepository.deleteAll()
    }

    private val body = """{"amount":1000,"currency":"EUR"}"""

    @Test
    fun `first request creates the payment and completes the record`() {
        mockMvc.post("/payments") {
            headers { set("Idempotency-Key", "key-A") }
            contentType = MediaType.APPLICATION_JSON
            content = body
        }.andExpect {
            status { isCreated() }
        }

        assertThat(paymentRepository.count()).isEqualTo(1)
        val record = idempotencyRecordRepository.findById("key-A").orElseThrow()
        assertThat(record.status).isEqualTo(IdempotencyStatus.COMPLETED)
        assertThat(record.responseStatus).isEqualTo(201)
        assertThat(record.responseBody).contains(""""currency":"EUR"""")
    }

    @Test
    fun `a duplicate key replays the original response byte-for-byte`() {
        val first = mockMvc.post("/payments") {
            headers { set("Idempotency-Key", "key-B") }
            contentType = MediaType.APPLICATION_JSON
            content = body
        }.andExpect { status { isCreated() } }
            .andReturn().response

        val second = mockMvc.post("/payments") {
            headers { set("Idempotency-Key", "key-B") }
            contentType = MediaType.APPLICATION_JSON
            content = body
        }.andExpect {
            status { isCreated() }
            header { string("Idempotent-Replayed", "true") }
        }.andReturn().response

        // The replay is indistinguishable from the original: same status, same body.
        assertThat(second.status).isEqualTo(first.status)
        assertThat(second.contentAsString).isEqualTo(first.contentAsString)
        // ...and only one payment was ever created.
        assertThat(paymentRepository.count()).isEqualTo(1)
        // The first response carried no replay header.
        assertThat(first.getHeader("Idempotent-Replayed")).isNull()
    }

    @Test
    fun `an in-flight duplicate returns 409 and creates no payment`() {
        // Simulate a first request that has claimed the key and is still mid-flight:
        // an IN_PROGRESS row exists but the work has not completed yet.
        val hash = requestHasher.hash(CreatePaymentCommand(amount = 1000, currency = "EUR"))
        idempotencyRecordRepository.saveAndFlush(
            IdempotencyRecord(key = "key-inflight", requestHash = hash, createdAt = Instant.parse("2026-01-01T00:00:00Z")),
        )

        mockMvc.post("/payments") {
            headers { set("Idempotency-Key", "key-inflight") }
            contentType = MediaType.APPLICATION_JSON
            content = body
        }.andExpect {
            status { isConflict() }
            header { string("Retry-After", "1") }
            jsonPath("$.code") { value("IN_PROGRESS") }
        }

        // The duplicate did NOT do the work...
        assertThat(paymentRepository.count()).isEqualTo(0)
        // ...and left the original's record untouched (still IN_PROGRESS).
        val record = idempotencyRecordRepository.findById("key-inflight").orElseThrow()
        assertThat(record.status).isEqualTo(IdempotencyStatus.IN_PROGRESS)
        assertThat(record.responseBody).isNull()
    }

    @Test
    fun `the same key with a different body is rejected with 422`() {
        mockMvc.post("/payments") {
            headers { set("Idempotency-Key", "key-mismatch") }
            contentType = MediaType.APPLICATION_JSON
            content = """{"amount":1000,"currency":"EUR"}"""
        }.andExpect { status { isCreated() } }

        mockMvc.post("/payments") {
            headers { set("Idempotency-Key", "key-mismatch") }
            contentType = MediaType.APPLICATION_JSON
            content = """{"amount":9999,"currency":"EUR"}"""
        }.andExpect {
            status { isUnprocessableEntity() }
            jsonPath("$.code") { value("IDEMPOTENCY_KEY_MISMATCH") }
        }

        // The mismatched retry neither replayed nor created a new payment.
        assertThat(paymentRepository.count()).isEqualTo(1)
    }

    @Test
    fun `a different key creates a separate payment`() {
        mockMvc.post("/payments") {
            headers { set("Idempotency-Key", "key-C1") }
            contentType = MediaType.APPLICATION_JSON
            content = body
        }
        mockMvc.post("/payments") {
            headers { set("Idempotency-Key", "key-C2") }
            contentType = MediaType.APPLICATION_JSON
            content = body
        }

        assertThat(paymentRepository.count()).isEqualTo(2)
    }
}
