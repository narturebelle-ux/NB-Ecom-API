package com.narturebelle.repository;

import com.narturebelle.model.PaidOrder;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaidOrderRepository extends JpaRepository<PaidOrder, Long> {

    /** Creates the row if the refund arrived first, so the payment arriving later can't count. */
    default void markRefunded(Long orderId, Long userId) {
        PaidOrder paidOrder = findById(orderId).orElseGet(() -> new PaidOrder(orderId, userId));
        paidOrder.setRefunded(true);
        save(paidOrder);
    }
}
