package com.narturebelle.repository;

import com.narturebelle.model.PaymentReconciliationFlag;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentReconciliationFlagRepository extends JpaRepository<PaymentReconciliationFlag, Long> {
    boolean existsByPaymentId(Long paymentId);
}
