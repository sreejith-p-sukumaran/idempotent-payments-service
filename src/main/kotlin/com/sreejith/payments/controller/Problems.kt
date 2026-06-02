package com.sreejith.payments.controller

import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail

/**
 * Builds RFC 7807 [ProblemDetail] responses with a stable, machine-readable
 * `code` so every error in this service has a consistent shape.
 */
object Problems {

    const val CODE_PROPERTY = "code"

    fun of(status: HttpStatus, code: String, detail: String): ProblemDetail =
        ProblemDetail.forStatusAndDetail(status, detail).apply {
            setProperty(CODE_PROPERTY, code)
        }
}
