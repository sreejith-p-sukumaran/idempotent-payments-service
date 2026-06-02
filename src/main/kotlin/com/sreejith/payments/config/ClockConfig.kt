package com.sreejith.payments.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

/**
 * Exposes a [Clock] bean so time-dependent logic (timestamps, expiry) can be
 * driven by a fixed clock in tests instead of the wall clock.
 */
@Configuration
class ClockConfig {

    @Bean
    fun clock(): Clock = Clock.systemUTC()
}
