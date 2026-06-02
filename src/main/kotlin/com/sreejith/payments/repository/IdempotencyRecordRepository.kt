package com.sreejith.payments.repository

import com.sreejith.payments.domain.IdempotencyRecord
import org.springframework.data.jpa.repository.JpaRepository

interface IdempotencyRecordRepository : JpaRepository<IdempotencyRecord, String>
