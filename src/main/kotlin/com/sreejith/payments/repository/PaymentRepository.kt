package com.sreejith.payments.repository

import com.sreejith.payments.domain.Payment
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface PaymentRepository : JpaRepository<Payment, UUID>
