package com.scantoorder.scantoorder.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// A schema that is rebuilt on every start may only run against this machine, unless ALLOW_SCHEMA_RESET is set.
class SchemaResetGuardTest {
    private static final String REMOTE = "jdbc:postgresql://db.example.com:5432/plately";

    @Test
    void createOnARemoteHostIsRefused() {
        IllegalStateException refused = assertThrows(IllegalStateException.class, () -> SchemaResetGuard.check("create", REMOTE, false));
        assertTrue(refused.getMessage().contains("ALLOW_SCHEMA_RESET"));
        assertThrows(IllegalStateException.class, () -> SchemaResetGuard.check("create-drop", REMOTE, false));
        assertThrows(IllegalStateException.class, () -> SchemaResetGuard.check("CREATE", REMOTE, false));
    }

    @Test
    void createOnThisMachineIsAllowed() {
        assertDoesNotThrow(() -> SchemaResetGuard.check("create", "jdbc:postgresql://localhost:5432/plately_design_local", false));
        assertDoesNotThrow(() -> SchemaResetGuard.check("create-drop", "jdbc:postgresql://127.0.0.1/plately?sslmode=disable", false));
    }

    @Test
    void aRemoteHostCannotHideBehindALocalLookingName() {
        assertThrows(IllegalStateException.class, () -> SchemaResetGuard.check("create", "jdbc:postgresql://localhost.evil.example:5432/x", false));
        assertThrows(IllegalStateException.class, () -> SchemaResetGuard.check("create", "jdbc:postgresql://user@db.example.com/x?host=localhost", false));
        assertThrows(IllegalStateException.class, () -> SchemaResetGuard.check("create", "not a url", false));
        assertThrows(IllegalStateException.class, () -> SchemaResetGuard.check("create", "", false));
    }

    @Test
    void theOverrideAllowsARemoteHost() {
        assertDoesNotThrow(() -> SchemaResetGuard.check("create", REMOTE, true));
    }

    @Test
    void modesThatKeepDataAreNeverBlocked() {
        assertDoesNotThrow(() -> SchemaResetGuard.check("update", REMOTE, false));
        assertDoesNotThrow(() -> SchemaResetGuard.check("validate", REMOTE, false));
        assertDoesNotThrow(() -> SchemaResetGuard.check("none", REMOTE, false));
        assertDoesNotThrow(() -> SchemaResetGuard.check("", REMOTE, false));
    }
}
