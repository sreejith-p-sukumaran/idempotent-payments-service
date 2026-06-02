package com.sreejith.payments.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.sreejith.payments.domain.CreatePaymentCommand
import org.springframework.stereotype.Component
import java.security.MessageDigest

/**
 * Produces a stable SHA-256 hash of a request so that a later request with the
 * same idempotency key can be checked for "same key, same operation".
 *
 * We hash the *normalized command* (a canonical JSON of the validated fields)
 * rather than the raw bytes, so cosmetic differences — whitespace, currency
 * casing — don't read as a different request. DESIGN.md flagged this as an open
 * question; the canonical form is the more robust choice.
 */
@Component
class RequestHasher(private val objectMapper: ObjectMapper) {

    fun hash(command: CreatePaymentCommand): String {
        val canonical = objectMapper.writeValueAsString(command)
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
