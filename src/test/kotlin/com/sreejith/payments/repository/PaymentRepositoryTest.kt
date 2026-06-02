package com.sreejith.payments.repository

import com.sreejith.payments.TestcontainersConfiguration
import com.sreejith.payments.domain.Payment
import com.sreejith.payments.domain.PaymentStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.context.annotation.Import
import java.time.Instant
import java.util.UUID

/**
 * Repository slice test against a real Postgres (Testcontainers). Verifies the
 * Flyway-created schema and the JPA mapping agree: a saved payment reloads with
 * every field intact.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration::class)
class PaymentRepositoryTest {

    @Autowired
    lateinit var paymentRepository: PaymentRepository

    @Test
    fun `persists and reloads a payment`() {
        val payment = Payment(
            id = UUID.randomUUID(),
            amount = 1_000,
            currency = "EUR",
            status = PaymentStatus.SUCCEEDED,
            createdAt = Instant.parse("2026-01-01T00:00:00Z"),
        )

        paymentRepository.saveAndFlush(payment)
        val reloaded = paymentRepository.findById(payment.id)

        assertThat(reloaded).isPresent
        with(reloaded.get()) {
            assertThat(amount).isEqualTo(1_000)
            assertThat(currency).isEqualTo("EUR")
            assertThat(status).isEqualTo(PaymentStatus.SUCCEEDED)
            assertThat(createdAt).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"))
        }
    }
}
