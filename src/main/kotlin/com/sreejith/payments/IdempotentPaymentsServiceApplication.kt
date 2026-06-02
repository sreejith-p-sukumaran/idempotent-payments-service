package com.sreejith.payments

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class IdempotentPaymentsServiceApplication

fun main(args: Array<String>) {
	runApplication<IdempotentPaymentsServiceApplication>(*args)
}
