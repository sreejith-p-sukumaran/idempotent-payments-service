package com.sreejith.payments.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * Tunables for idempotency-record lifetime. Bound from the `idempotency.*`
 * config so the retention window and sweep cadence are not hardcoded
 * (DESIGN.md open questions).
 */
@ConfigurationProperties(prefix = "idempotency")
data class IdempotencyProperties(
    /**
     * How long a record is kept. Long enough to serve a late retry (replay),
     * short enough to bound table growth and stop honoring a key forever.
     */
    val retention: Duration = Duration.ofHours(24),
    val cleanup: Cleanup = Cleanup(),
) {
    data class Cleanup(
        /** How often the expiry sweep runs. */
        val interval: Duration = Duration.ofHours(1),
    )
}
