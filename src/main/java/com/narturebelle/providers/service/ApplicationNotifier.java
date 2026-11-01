package com.narturebelle.providers.service;

import com.narturebelle.email.EmailMessage;
import com.narturebelle.email.EmailSender;
import com.narturebelle.repository.UserRepository;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Sends status emails on a small background pool after the transaction commits, so a slow mail service can
 * never hold a request or a database connection. A failed send is logged and never fails the action.
 */
@Component
public class ApplicationNotifier {

    private static final Logger log = LoggerFactory.getLogger(ApplicationNotifier.class);
    private static final String SIGN_OFF = "\n\n-- The NartureBelle team";
    private static final int QUEUE_SIZE = 200;

    private final EmailSender emailSender;
    private final UserRepository userRepository;
    private final ExecutorService sendPool = new ThreadPoolExecutor(1, 2, 60, TimeUnit.SECONDS, new ArrayBlockingQueue<>(QUEUE_SIZE));

    public ApplicationNotifier(EmailSender emailSender, UserRepository userRepository) {
        this.emailSender = emailSender;
        this.userRepository = userRepository;
    }

    public void notifyUser(Long userId, String subject, String body) {
        userRepository.findById(userId)
                .filter(user -> user.getEmail() != null && !user.getEmail().isBlank())
                .ifPresent(user -> afterCommit(() -> enqueue(user.getEmail(), subject, body)));
    }

    private void enqueue(String to, String subject, String body) {
        try {
            sendPool.execute(() -> sendQuietly(to, subject, body));
        } catch (RejectedExecutionException e) {
            log.warn("Email queue is full; dropped '{}'", subject);
        }
    }

    @PreDestroy
    void shutDown() {
        sendPool.shutdown();
    }

    private void afterCommit(Runnable send) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send.run();
                }
            });
        } else {
            send.run();
        }
    }

    private void sendQuietly(String to, String subject, String body) {
        try {
            emailSender.send(new EmailMessage(to, null, subject, body + SIGN_OFF, null));
        } catch (Exception e) {
            log.warn("Could not send email '{}': {}", subject, e.getMessage());
        }
    }
}
