package com.narturebelle.mail;

import com.narturebelle.repository.FeedbackMessageRepository;
import com.narturebelle.repository.JobApplicationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;

/** Calls Mails for one row at a time, with no transaction open during the call. Logs IDs only. */
@Component
public class OutgoingEmailDispatcher {

    /** About a day of scheduler runs. FAILED rows keep their content for the failed-email list (EMAIL_TASKS M7.1). */
    static final int MAX_ATTEMPTS = 96;
    private static final int RETRY_BATCH = 10;

    private static final Logger log = LoggerFactory.getLogger(OutgoingEmailDispatcher.class);

    private final OutgoingEmailRepository outgoingEmailRepository;
    private final MailsClient mailsClient;
    private final JobApplicationRepository jobApplicationRepository;
    private final FeedbackMessageRepository feedbackMessageRepository;
    private final TransactionTemplate requiresNew;

    public OutgoingEmailDispatcher(OutgoingEmailRepository outgoingEmailRepository, MailsClient mailsClient,
                                   JobApplicationRepository jobApplicationRepository,
                                   FeedbackMessageRepository feedbackMessageRepository,
                                   PlatformTransactionManager transactionManager) {
        this.outgoingEmailRepository = outgoingEmailRepository;
        this.mailsClient = mailsClient;
        this.jobApplicationRepository = jobApplicationRepository;
        this.feedbackMessageRepository = feedbackMessageRepository;
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Never throws: a row that wasn't sent stays for the next scheduler run. */
    public void dispatch(Long id) {
        OutgoingEmail row = requiresNew.execute(status -> outgoingEmailRepository.findById(id).orElse(null));
        if (row == null || row.getStatus() != OutgoingEmail.Status.PENDING) {
            return;
        }
        try {
            boolean delivered = call(row);
            requiresNew.executeWithoutResult(status -> markSent(id, delivered));
        } catch (Exception e) {
            log.warn("Email row {} not sent yet: {}", id, e.getClass().getSimpleName());
            requiresNew.executeWithoutResult(status -> outgoingEmailRepository.findById(id)
                    .ifPresent(failed -> failed.recordFailedAttempt(MAX_ATTEMPTS)));
        }
    }

    /** Called by the scheduler. Returns how many rows it tried. */
    public int retryDue() {
        LocalDateTime now = LocalDateTime.now();
        List<Long> ids = requiresNew.execute(status -> {
            List<Long> locked = outgoingEmailRepository.lockDue(now.minusMinutes(1), RETRY_BATCH);
            if (!locked.isEmpty()) {
                outgoingEmailRepository.stampAttempt(locked, now);
            }
            return locked;
        });
        ids.forEach(this::dispatch);
        return ids.size();
    }

    private boolean call(OutgoingEmail row) {
        if (row.getKind() == OutgoingEmail.Kind.TRANSACTIONAL) {
            mailsClient.sendTransactional(row.getUserId(), row.getSubject(), row.getBody());
            return false;
        }
        return mailsClient.send(new OutgoingMail(row.getEventId(), row.getEventType(), row.getUserId(), row.getToAddress(),
                row.getReplyTo(), row.getSubject(), row.getBody()));
    }

    private void markSent(Long id, boolean delivered) {
        OutgoingEmail row = outgoingEmailRepository.findById(id).orElseThrow();
        row.markSent();
        if (!delivered || row.getOnDelivered() == null) {
            return;
        }
        switch (row.getOnDelivered()) {
            case JOB_APPLICATION -> jobApplicationRepository.findById(row.getSourceId())
                    .ifPresent(application -> application.setAdminNotifiedAt(LocalDateTime.now()));
            case FEEDBACK -> feedbackMessageRepository.findById(row.getSourceId())
                    .ifPresent(feedback -> feedback.setAdminNotifiedAt(LocalDateTime.now()));
        }
    }
}
