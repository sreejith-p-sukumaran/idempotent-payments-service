package com.sreejith.payments.config

import com.sreejith.payments.service.IdempotencyCleanupTask
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.SchedulingConfigurer
import org.springframework.scheduling.config.ScheduledTaskRegistrar

/**
 * Registers the expiry sweep at a fixed rate read from configuration, so the
 * cadence lives in [IdempotencyProperties] rather than a hardcoded annotation
 * value.
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(IdempotencyProperties::class)
class IdempotencyCleanupConfig(
    private val properties: IdempotencyProperties,
    private val cleanupTask: IdempotencyCleanupTask,
) : SchedulingConfigurer {

    override fun configureTasks(taskRegistrar: ScheduledTaskRegistrar) {
        taskRegistrar.addFixedRateTask(
            { cleanupTask.removeExpired() },
            properties.cleanup.interval.toMillis(),
        )
    }
}
