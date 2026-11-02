package com.narturebelle.controller;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** BUG-37 sign-in cases on the usual test database. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FirebaseSignInH2Test extends FirebaseSignInScenarios {
}
