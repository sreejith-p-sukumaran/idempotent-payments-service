package com.sreejith.payments

import com.sreejith.payments.repository.IdempotencyRecordRepository
import com.sreejith.payments.repository.PaymentRepository
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

/**
 * Full-stack integration test: real Spring context + real Postgres
 * (Testcontainers) + Flyway migrations. Exercises the endpoint end to end and
 * asserts the side effect on the database.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
class PaymentApiIntegrationTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var paymentRepository: PaymentRepository

    @Autowired
    lateinit var idempotencyRecordRepository: IdempotencyRecordRepository

    @BeforeEach
    fun clean() {
        paymentRepository.deleteAll()
        idempotencyRecordRepository.deleteAll()
    }

    @Test
    fun `POST payments persists exactly one payment and returns it`() {
        mockMvc.post("/payments") {
            headers { set("Idempotency-Key", "integration-key-1") }
            contentType = MediaType.APPLICATION_JSON
            content = """{"amount":2500,"currency":"usd"}"""
        }.andExpect {
            status { isCreated() }
            jsonPath("$.id") { exists() }
            jsonPath("$.amount") { value(2_500) }
            jsonPath("$.currency") { value("USD") }
            jsonPath("$.status") { value("SUCCEEDED") }
            jsonPath("$.createdAt") { exists() }
        }

        assertThat(paymentRepository.count()).isEqualTo(1)
    }
}
