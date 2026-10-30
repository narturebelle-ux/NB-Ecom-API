package com.narturebelle.repository;

import com.narturebelle.model.Child;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChildRepository extends JpaRepository<Child, Long> {
    List<Child> findByUserIdOrderByDateOfBirthAsc(Long userId);
    Optional<Child> findByIdAndUserId(Long id, Long userId);
}
