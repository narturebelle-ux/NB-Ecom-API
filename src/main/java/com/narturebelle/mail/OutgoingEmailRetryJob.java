package com.narturebelle.mail;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** In-app retries, for as long as the container is awake. The external scheduler (D18) calls the internal endpoint instead. */
@Component
@ConditionalOnProperty(prefix = "narturebelle.scheduling", name = "enabled", matchIfMissing = true)
public class OutgoingEmailRetryJob {

    private final OutgoingEmailDispatcher dispatcher;

    public OutgoingEmailRetryJob(OutgoingEmailDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @Scheduled(fixedRate = 900_000) // every 15 minutes
    public void retry() {
        dispatcher.retryDue();
    }
}
