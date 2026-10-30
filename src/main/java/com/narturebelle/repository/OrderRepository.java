package com.narturebelle.repository;

import com.narturebelle.model.Order;
import com.narturebelle.model.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {
    Optional<Order> findByUserIdAndIdempotencyKey(Long userId, String idempotencyKey);
    List<Order> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<Order> findByStatusIn(List<OrderStatus> statuses);
    boolean existsByUserIdAndStatus(Long userId, OrderStatus status);

    /** Orders whose price was counted from a due date -- they lock how far the EDD may move. */
    List<Order> findByUserIdAndStatusInAndPricedDueDateIsNotNull(Long userId, List<OrderStatus> statuses);
}
