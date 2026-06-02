package com.sreejith.payments.controller

import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/**
 * Centralizes request-level error rendering as RFC 7807 [ProblemDetail]s, so
 * malformed input never surfaces as a raw 500 or a framework error page.
 */
@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(MissingRequestHeaderException::class, MissingIdempotencyKeyException::class)
    fun handleMissingIdempotencyKey(ex: Exception): ProblemDetail =
        Problems.of(
            HttpStatus.BAD_REQUEST,
            "MISSING_IDEMPOTENCY_KEY",
            "The Idempotency-Key header is required and must not be blank.",
        )

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(ex: MethodArgumentNotValidException): ProblemDetail {
        val problem = Problems.of(
            HttpStatus.BAD_REQUEST,
            "VALIDATION_FAILED",
            "One or more fields are invalid.",
        )
        val fieldErrors = ex.bindingResult.fieldErrors
            .associate { it.field to (it.defaultMessage ?: "invalid") }
        problem.setProperty("errors", fieldErrors)
        return problem
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleUnreadableBody(ex: HttpMessageNotReadableException): ProblemDetail =
        Problems.of(
            HttpStatus.BAD_REQUEST,
            "MALFORMED_REQUEST",
            "The request body is missing or malformed.",
        )
}
