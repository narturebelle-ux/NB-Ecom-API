package com.narturebelle.repository;

import com.narturebelle.model.HospitalPartner;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface HospitalPartnerRepository extends JpaRepository<HospitalPartner, Long> {
    List<HospitalPartner> findByCityIgnoreCase(String city);
    List<HospitalPartner> findByPincode(String pincode);
}
