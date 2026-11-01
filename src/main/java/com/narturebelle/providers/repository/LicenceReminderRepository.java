package com.narturebelle.providers.repository;

import com.narturebelle.providers.model.LicenceReminder;
import java.time.LocalDate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LicenceReminderRepository extends JpaRepository<LicenceReminder, Long> {

    /** A written query, because a derived name would read "Before" in daysBefore as a keyword. */
    @Query("select count(r) > 0 from LicenceReminder r where r.applicationId = :applicationId and r.expiresOn = :expiresOn and r.daysBefore = :days")
    boolean alreadySent(@Param("applicationId") Long applicationId, @Param("expiresOn") LocalDate expiresOn, @Param("days") int days);
}
