package com.narturebelle.providers.scheduling;

import com.narturebelle.providers.model.ApplicationStatus;
import com.narturebelle.providers.model.LicenceReminder;
import com.narturebelle.providers.model.ProviderApplication;
import com.narturebelle.providers.repository.ApplicationAnswerRepository;
import com.narturebelle.providers.repository.LicenceReminderRepository;
import com.narturebelle.providers.repository.ProviderApplicationRepository;
import com.narturebelle.providers.repository.RequestedChangeRepository;
import com.narturebelle.security.AuditService;
import com.narturebelle.providers.service.ApplicationDocumentService;
import com.narturebelle.providers.service.ApplicationNotifier;
import com.narturebelle.providers.service.ProviderLifecycleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * The approved time rules: reminders, closing stale applications, licence expiry and data deletion. Safe to
 * re-run, and each application is handled in its own transaction so one failure can't stop the rest.
 * Off in the test profile; tests call runDaily() directly.
 */
@Component
@ConditionalOnProperty(prefix = "narturebelle.scheduling", name = "enabled", matchIfMissing = true)
public class ApplicationLifecycleJob {

    private static final Logger log = LoggerFactory.getLogger(ApplicationLifecycleJob.class);

    private final ProviderApplicationRepository applicationRepository;
    private final ApplicationAnswerRepository answerRepository;
    private final RequestedChangeRepository requestedChangeRepository;
    private final LicenceReminderRepository reminderRepository;
    private final ApplicationDocumentService documentService;
    private final ProviderLifecycleService lifecycleService;
    private final ApplicationNotifier notifier;
    private final AuditService auditService;
    private final TransactionTemplate transactionTemplate;
    private final int firstReminderDays;
    private final int secondReminderDays;
    private final int draftDeleteDays;
    private final int changesCloseDays;
    private final int recheckAfterDays;
    private final List<Integer> licenceReminderDays;
    private final int retentionMonths;

    public ApplicationLifecycleJob(ProviderApplicationRepository applicationRepository, ApplicationAnswerRepository answerRepository,
                                   RequestedChangeRepository requestedChangeRepository, LicenceReminderRepository reminderRepository,
                                   ApplicationDocumentService documentService, ProviderLifecycleService lifecycleService,
                                   ApplicationNotifier notifier, AuditService auditService, TransactionTemplate transactionTemplate,
                                   @Value("${onboarding.draft-first-reminder-days}") int firstReminderDays,
                                   @Value("${onboarding.draft-second-reminder-days}") int secondReminderDays,
                                   @Value("${onboarding.draft-delete-days}") int draftDeleteDays,
                                   @Value("${onboarding.changes-close-days}") int changesCloseDays,
                                   @Value("${onboarding.recheck-after-days}") int recheckAfterDays,
                                   @Value("${onboarding.licence-reminder-days}") List<Integer> licenceReminderDays,
                                   @Value("${onboarding.retention-months}") int retentionMonths) {
        this.applicationRepository = applicationRepository;
        this.answerRepository = answerRepository;
        this.requestedChangeRepository = requestedChangeRepository;
        this.reminderRepository = reminderRepository;
        this.documentService = documentService;
        this.lifecycleService = lifecycleService;
        this.notifier = notifier;
        this.auditService = auditService;
        this.transactionTemplate = transactionTemplate;
        this.firstReminderDays = firstReminderDays;
        this.secondReminderDays = secondReminderDays;
        this.draftDeleteDays = draftDeleteDays;
        this.changesCloseDays = changesCloseDays;
        this.recheckAfterDays = recheckAfterDays;
        this.licenceReminderDays = licenceReminderDays;
        this.retentionMonths = retentionMonths;
    }

    /** The hosted container sleeps when idle and can miss the nightly time, so also catch up whenever it wakes. Safe to re-run. */
    @EventListener(ApplicationReadyEvent.class)
    public void catchUpOnStart() {
        try {
            runDaily();
        } catch (Exception e) {
            log.error("Lifecycle job failed on startup", e);
        }
    }

    @Scheduled(cron = "0 30 3 * * *", zone = "Asia/Kolkata")
    public void runDaily() {
        LocalDateTime now = LocalDateTime.now();
        forEach(ApplicationStatus.DRAFT, application -> handleDraft(application, now));
        forEach(ApplicationStatus.CHANGES_REQUESTED, application -> closeIfUnanswered(application, now));
        forEach(ApplicationStatus.VERIFIED, application -> flagForRecheck(application, now));
        for (ApplicationStatus held : List.of(ApplicationStatus.VERIFIED, ApplicationStatus.ACTIVE, ApplicationStatus.PAUSED, ApplicationStatus.SUSPENDED)) {
            forEach(held, application -> handleLicence(application, now.toLocalDate()));
        }
        for (ApplicationStatus closed : List.of(ApplicationStatus.REJECTED, ApplicationStatus.WITHDRAWN, ApplicationStatus.CLOSED)) {
            forEach(closed, application -> purgeIfExpired(application, now));
        }
    }

    private void forEach(ApplicationStatus status, java.util.function.Consumer<ProviderApplication> action) {
        for (ProviderApplication application : applicationRepository.findByStatus(status)) {
            try {
                transactionTemplate.executeWithoutResult(tx -> action.accept(application));
            } catch (Exception e) {
                log.error("Lifecycle job failed for application {}", application.getId(), e);
            }
        }
    }

    private void handleDraft(ProviderApplication application, LocalDateTime now) {
        LocalDateTime lastActivity = application.getLastActivityAt();
        if (lastActivity.isBefore(now.minusDays(draftDeleteDays))) {
            deleteDraft(application);
            return;
        }
        int reminders = application.getReminderCount();
        boolean due = (reminders == 0 && lastActivity.isBefore(now.minusDays(firstReminderDays)))
                || (reminders == 1 && lastActivity.isBefore(now.minusDays(secondReminderDays)));
        if (due) {
            application.setReminderCount(reminders + 1);
            applicationRepository.save(application);
            notifier.notifyUser(application.getUserId(), "Finish your NartureBelle application",
                    "Your application is saved. Sign in any time to pick up where you left off.");
        }
    }

    private void deleteDraft(ProviderApplication application) {
        documentService.removeAll(application.getId());
        answerRepository.deleteByApplicationId(application.getId());
        requestedChangeRepository.deleteByApplicationId(application.getId());
        applicationRepository.delete(application);
        auditService.record("PROVIDER_DRAFT_DELETED", "PROVIDER_APPLICATION", String.valueOf(application.getId()), "No activity for " + draftDeleteDays + " days");
    }

    private void closeIfUnanswered(ProviderApplication application, LocalDateTime now) {
        if (application.getChangesRequestedAt() != null && application.getChangesRequestedAt().isBefore(now.minusDays(changesCloseDays))) {
            application.setStatus(ApplicationStatus.CLOSED);
            application.setClosedAt(now);
            applicationRepository.save(application);
            auditService.record("PROVIDER_APPLICATION_CLOSED", "PROVIDER_APPLICATION", String.valueOf(application.getId()), "Requested changes not answered");
            notifier.notifyUser(application.getUserId(), "Your NartureBelle application was closed",
                    "We didn't receive the changes we asked for within " + changesCloseDays + " days, so your application was closed. You're welcome to apply again.");
        }
    }

    private void flagForRecheck(ProviderApplication application, LocalDateTime now) {
        if (!application.isRecheckRequired() && application.getVerifiedAt() != null && application.getVerifiedAt().isBefore(now.minusDays(recheckAfterDays))) {
            application.setRecheckRequired(true);
            applicationRepository.save(application);
            auditService.record("PROVIDER_RECHECK_REQUIRED", "PROVIDER_APPLICATION", String.valueOf(application.getId()), "Setup not finished in time");
        }
    }

    private void handleLicence(ProviderApplication application, LocalDate today) {
        LocalDate expires = application.getLicenceExpiresOn();
        if (expires == null) {
            return;
        }
        if (expires.isBefore(today)) {
            if (application.getStatus() == ApplicationStatus.ACTIVE) {
                lifecycleService.pauseForLapsedLicence(application);
            } else if (!application.isLicenceLapsed()) {
                // Not live, so nothing to hide or cancel, but it must not go live or be restored on this licence.
                application.setLicenceLapsed(true);
                applicationRepository.save(application);
                auditService.record("PROVIDER_LICENCE_LAPSED", "PROVIDER_APPLICATION", String.valueOf(application.getId()), "Licence expired");
                notifier.notifyUser(application.getUserId(), "Your licence has expired",
                        "Your licence on file with NartureBelle has expired. Please upload your renewed licence from your dashboard.");
            }
            return;
        }
        for (int days : licenceReminderDays) {
            boolean withinWindow = !expires.isAfter(today.plusDays(days));
            boolean closerWindowApplies = licenceReminderDays.stream().anyMatch(other -> other < days && !expires.isAfter(today.plusDays(other)));
            if (withinWindow && !closerWindowApplies
                    && !reminderRepository.alreadySent(application.getId(), expires, days)) {
                LicenceReminder reminder = new LicenceReminder();
                reminder.setApplicationId(application.getId());
                reminder.setExpiresOn(expires);
                reminder.setDaysBefore(days);
                reminderRepository.save(reminder);
                notifier.notifyUser(application.getUserId(), "Your licence expires soon",
                        "Your licence on file with NartureBelle expires on " + expires + ". Please upload your renewed licence from your dashboard so your profile stays live.");
            }
        }
    }

    /** Deletes documents and answers, keeping the registration number, decision and reason to catch repeat fraud. */
    private void purgeIfExpired(ProviderApplication application, LocalDateTime now) {
        if (application.getPurgedAt() == null && application.getClosedAt() != null && application.getClosedAt().isBefore(now.minusMonths(retentionMonths))) {
            documentService.removeAll(application.getId());
            answerRepository.deleteByApplicationId(application.getId());
            requestedChangeRepository.deleteByApplicationId(application.getId());
            application.setPurgedAt(now);
            applicationRepository.save(application);
            auditService.record("PROVIDER_APPLICATION_PURGED", "PROVIDER_APPLICATION", String.valueOf(application.getId()), "Retention period ended");
        }
    }
}
