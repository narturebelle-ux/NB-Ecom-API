package com.narturebelle.repository;

import com.narturebelle.model.NurseBooking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NurseBookingRepository extends JpaRepository<NurseBooking, Long> {
    List<NurseBooking> findByClientPhone(String clientPhone);
}
