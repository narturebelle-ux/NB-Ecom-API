package com.narturebelle.mail;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * The only way the Care API sends email. Saves a row in the caller's transaction, so a rollback sends nothing and a
 * crash loses nothing, then hands the row to a small background pool after commit. The request never waits on Mails,
 * and no database connection is held during the call. A full queue or a frozen container just leaves the row for the
 * scheduler. Don't call this from an afterCommit callback: the save would join the finished transaction and be lost.
 */
@Service
public class MailQueue {

    private static final Logger log = LoggerFactory.getLogger(MailQueue.class);
    private static final int QUEUE_SIZE = 200;

    private final OutgoingEmailRepository outgoingEmailRepository;
    private final OutgoingEmailDispatcher dispatcher;
    private final boolean async;
    private final ExecutorService sendPool = new ThreadPoolExecutor(1, 2, 60, TimeUnit.SECONDS, new ArrayBlockingQueue<>(QUEUE_SIZE));

    public MailQueue(OutgoingEmailRepository outgoingEmailRepository, OutgoingEmailDispatcher dispatcher,
                     @Value("${mails.async-dispatch:true}") boolean async) {
        this.outgoingEmailRepository = outgoingEmailRepository;
        this.dispatcher = dispatcher;
        this.async = async;
    }

    public void sendToAddress(String eventType, String to, String replyTo, String subject, String text) {
        enqueue(email(eventType, to, null, replyTo, subject, text));
    }

    /**
     * Like sendToUser, with a fixed event ID: the outbox's unique event ID refuses a second copy, and
     * NB-Ecom-Notifications-API treats a repeated event ID as already sent. For "send this once" emails.
     * Must run in the caller's transaction, so a duplicate rolls back with it.
     */
    public void sendToUserOnce(String eventId, String eventType, Long userId, String subject, String text) {
        OutgoingEmail row = email(eventType, null, userId, null, subject, text);
        row.setEventId(eventId);
        enqueue(row);
    }

    public void sendToUser(String eventType, Long userId, String subject, String text) {
        enqueue(email(eventType, null, userId, null, subject, text));
    }

    /** Records adminNotifiedAt on the feedback row once the email really went out. */
    public void sendFeedbackNotice(Long feedbackId, String to, String replyTo, String subject, String text) {
        OutgoingEmail row = email("FEEDBACK_ADMIN", to, null, replyTo, subject, text);
        row.setOnDelivered(OutgoingEmail.OnDelivered.FEEDBACK);
        row.setSourceId(feedbackId);
        enqueue(row);
    }

    /** Records adminNotifiedAt on the application once the email really went out. */
    public void sendJobApplicationNotice(Long applicationId, String to, String replyTo, String subject, String text) {
        OutgoingEmail row = email("JOB_APPLICATION_ADMIN", to, null, replyTo, subject, text);
        row.setOnDelivered(OutgoingEmail.OnDelivered.JOB_APPLICATION);
        row.setSourceId(applicationId);
        enqueue(row);
    }

    /** The notification channel for transactional messages, which is still a NoOp in Mails. */
    public void sendTransactional(Long userId, String title, String body) {
        OutgoingEmail row = email("TRANSACTIONAL", null, userId, null, title, body);
        row.setKind(OutgoingEmail.Kind.TRANSACTIONAL);
        enqueue(row);
    }

    private OutgoingEmail email(String eventType, String to, Long userId, String replyTo, String subject, String text) {
        OutgoingEmail row = new OutgoingEmail();
        row.setEventId("care-" + UUID.randomUUID());
        row.setEventType(eventType);
        row.setKind(OutgoingEmail.Kind.EMAIL);
        row.setToAddress(to);
        row.setUserId(userId);
        row.setReplyTo(replyTo);
        row.setSubject(subject);
        row.setBody(text);
        return row;
    }

    private void enqueue(OutgoingEmail row) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            Long id = outgoingEmailRepository.save(row).getId();
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    handOff(id);
                }
            });
            return;
        }
        // No transaction to share, so the action is already saved: a problem here must not fail it.
        try {
            handOff(outgoingEmailRepository.save(row).getId());
        } catch (Exception e) {
            log.warn("Could not queue a {} email: {}", row.getEventType(), e.getClass().getSimpleName());
        }
    }

    private void handOff(Long id) {
        if (!async) {
            dispatcher.dispatch(id);
            return;
        }
        try {
            sendPool.execute(() -> dispatcher.dispatch(id));
        } catch (RejectedExecutionException e) {
            log.warn("Email queue is full; row {} waits for the scheduler", id);
        }
    }

    @PreDestroy
    void shutDown() {
        sendPool.shutdown();
    }
}
