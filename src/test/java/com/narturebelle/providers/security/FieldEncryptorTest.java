package com.narturebelle.providers.security;

import com.narturebelle.providers.exception.ProviderOnboardingException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FieldEncryptorTest {

    private static final String KEY = "MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE=";

    @Test
    void roundTripsWithAKey() {
        FieldEncryptor encryptor = new FieldEncryptor(KEY);
        String sealed = encryptor.encrypt("ABCDE1234F");
        assertNotEquals("ABCDE1234F", sealed);
        assertEquals("ABCDE1234F", encryptor.decrypt(sealed));
    }

    @Test
    void refusesSensitiveFieldsWithoutAKey() {
        FieldEncryptor encryptor = new FieldEncryptor("");
        ProviderOnboardingException encrypt = assertThrows(ProviderOnboardingException.class, () -> encryptor.encrypt("ABCDE1234F"));
        assertEquals(503, encrypt.getStatus());
        assertEquals("ENCRYPTION_NOT_CONFIGURED", encrypt.getCode());
        assertThrows(ProviderOnboardingException.class, () -> encryptor.decrypt("anything"));
    }
}
