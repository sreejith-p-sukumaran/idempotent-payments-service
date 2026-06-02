package com.sreejith.payments.domain

/**
 * The captured outcome of an idempotent operation: the HTTP status code and the
 * serialized response body, stored verbatim so a retry can be replayed
 * byte-for-byte without re-running the business logic.
 */
data class StoredResponse(
    val httpStatus: Int,
    val body: String,
)
