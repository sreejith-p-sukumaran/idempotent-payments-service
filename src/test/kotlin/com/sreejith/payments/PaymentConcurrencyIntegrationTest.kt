package com.sreejith.payments

import com.sreejith.payments.repository.IdempotencyRecordRepository
import com.sreejith.payments.repository.PaymentRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.context.annotation.Import
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * The test that matters most (DESIGN.md "The test that matters most").
 *
 * Fire many simultaneous POST /payments with the SAME idempotency key and prove
 * the design holds under the exact race it exists for: exactly one payment is
 * created, and every response is either the (replayed) success or a 409 — never
 * a second distinct payment.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    // More connections than threads so a blocked INSERT can never starve the
    // winner's connection for its follow-up work.
    properties = ["spring.datasource.hikari.maximum-pool-size=25"],
)
@Import(TestcontainersConfiguration::class)
class PaymentConcurrencyIntegrationTest {

    @Autowired
    lateinit var restTemplate: TestRestTemplate

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
    fun `concurrent requests with the same key create exactly one payment`() {
        val threadCount = 20
        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            set("Idempotency-Key", "race-key")
        }
        val request = HttpEntity("""{"amount":4200,"currency":"EUR"}""", headers)

        val pool = Executors.newFixedThreadPool(threadCount)
        val ready = CountDownLatch(threadCount)
        val fire = CountDownLatch(1)

        val futures = (1..threadCount).map {
            pool.submit<ResponseEntity<String>> {
                ready.countDown()
                fire.await() // line everyone up...
                restTemplate.postForEntity("/payments", request, String::class.java)
            }
        }

        ready.await(10, TimeUnit.SECONDS)
        fire.countDown() // ...then release them all at once
        val responses = futures.map { it.get(20, TimeUnit.SECONDS) }
        pool.shutdown()

        val statuses = responses.map { it.statusCode.value() }
        // Every response is either a (fresh or replayed) 201 or an in-flight 409.
        assertThat(statuses).allMatch { it == 201 || it == 409 }
        // At least one request won and did the work.
        assertThat(statuses).contains(201)

        // The decisive assertions: one payment, one record, no duplicates.
        assertThat(paymentRepository.count()).isEqualTo(1)
        assertThat(idempotencyRecordRepository.count()).isEqualTo(1)

        // Every successful response describes the same single payment.
        val successBodies = responses.filter { it.statusCode.value() == 201 }.map { it.body }.toSet()
        assertThat(successBodies).hasSize(1)
    }
}
