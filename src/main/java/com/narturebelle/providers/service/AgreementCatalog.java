package com.narturebelle.providers.service;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * The application terms and the professional agreement. DRAFT placeholders pending legal review, same
 * precedent as NB-Ecom-API's DRAFT consent documents; prod refuses signing until the final text is in
 * (onboarding.agreement-signing-enabled).
 */
@Component
public class AgreementCatalog {

    public static final String TERMS_VERSION = "DRAFT-1";
    public static final String AGREEMENT_VERSION = "DRAFT-1";
    public static final boolean IS_DRAFT = true;

    public String termsText() {
        return """
                Application terms (DRAFT)

                You are applying to offer care through NartureBelle. Sending an application does not make you a NartureBelle professional. Only approved professionals who finish setup can take sessions.

                You agree that NartureBelle may check your details and documents with official registers, your references and your institutes, and may keep them for the period set out in our privacy notice.

                This is placeholder text pending legal review. Do not treat this as a final or binding document.""";
    }

    public String agreementText(BigDecimal feePerSession) {
        return """
                Professional Agreement (DRAFT)

                Fee: NartureBelle pays you a fixed fee of Rs. %s for each completed session. Professionals do not set their own fee.

                Your duties: keep your registration valid and tell us at once if it changes; follow the law and your council's rules; keep mothers' information private; never reveal or help determine a baby's sex; refer emergencies to emergency care straight away.

                Product suggestions: suggest products only where they fit what the mother has shared in the session, and declare any financial ties.

                This is placeholder text pending legal review. Do not treat this as a final or binding document.""".formatted(feePerSession.toPlainString());
    }
}
