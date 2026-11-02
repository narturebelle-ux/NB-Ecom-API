package com.narturebelle.mail;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** A default here would let a missing variable pass startup and quietly queue mail until the rows fail a day later. */
class RequiredSettingsTest {

    @Test
    void prodAndStage_haveNoDefaults() throws IOException {
        for (String file : List.of("application-prod.yml", "application-stage.yml")) {
            String yml = new ClassPathResource(file).getContentAsString(StandardCharsets.UTF_8);
            for (String name : List.of("MAILS_API_URL", "INTERNAL_API_TOKEN", "REMINDER_DEDUPE_SECRET")) {
                assertTrue(yml.contains("${" + name + "}"), file + " needs " + name + " with no default");
            }
        }
    }
}
