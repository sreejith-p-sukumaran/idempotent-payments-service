package com.sreejith.payments

import org.springframework.boot.fromApplication
import org.springframework.boot.with


fun main(args: Array<String>) {
	fromApplication<IdempotentPaymentsServiceApplication>().with(TestcontainersConfiguration::class).run(*args)
}
