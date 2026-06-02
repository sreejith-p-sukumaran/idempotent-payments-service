package com.sreejith.payments.controller

/**
 * Thrown when the Idempotency-Key header is present but blank. (A completely
 * absent header is reported by Spring as MissingRequestHeaderException.) Both
 * map to the same 400 in [GlobalExceptionHandler].
 */
class MissingIdempotencyKeyException :
    RuntimeException("The Idempotency-Key header must not be blank")
