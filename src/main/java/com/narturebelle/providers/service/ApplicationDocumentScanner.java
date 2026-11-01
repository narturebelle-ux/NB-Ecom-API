package com.narturebelle.providers.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * NO REAL SCANNING HAPPENS. Same deferral as NB-Ecom-API's NoOpApplicationDocumentScanner: the file-type check in
 * ApplicationDocumentService is the only protection today. Swap this class for a real scanner before
 * reviewers open documents from the public in production.
 */
@Component
public class ApplicationDocumentScanner {

    private static final Logger log = LoggerFactory.getLogger(ApplicationDocumentScanner.class);

    public void scan(byte[] content) {
        log.debug("[NO-OP SCAN] Accepted {} bytes without scanning.", content.length);
    }
}
