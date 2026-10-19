package com.narturebelle.repository;

import com.narturebelle.model.NursePackage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NursePackageRepository extends JpaRepository<NursePackage, Long> {
}
