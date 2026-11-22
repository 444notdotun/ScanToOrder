package com.scantoorder.scantoorder.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Locale;

/**
 * Stops the app from wiping a database that is not on this machine. With DDL_AUTO set to create or create-drop the
 * schema is rebuilt on every start, which empties the data. That is fine on localhost and a disaster anywhere else,
 * so a remote host needs ALLOW_SCHEMA_RESET=true as a deliberate choice.
 */
@Component
public class SchemaResetGuard {
    @Value("${spring.jpa.hibernate.ddl-auto:}")
    private String schemaMode;

    @Value("${spring.datasource.url:}")
    private String databaseUrl;

    @Value("${app.schema.allow-reset:false}")
    private boolean allowReset;

    @PostConstruct
    void validate() {
        check(schemaMode, databaseUrl, allowReset);
    }

    static void check(String schemaMode, String databaseUrl, boolean allowReset) {
        if (allowReset || !rebuilds(schemaMode) || isLocal(databaseUrl)) {
            return;
        }
        throw new IllegalStateException("DDL_AUTO=" + schemaMode.trim() + " empties the database on every start, and DB_URL does not point at "
                + "localhost or 127.0.0.1. Use DDL_AUTO=update or validate for a database that holds real data. "
                + "If you really mean to wipe this one, set ALLOW_SCHEMA_RESET=true.");
    }

    private static boolean rebuilds(String schemaMode) {
        String mode = schemaMode == null ? "" : schemaMode.trim().toLowerCase(Locale.ROOT);
        return mode.equals("create") || mode.equals("create-drop");
    }

    // Only the host counts. An address that cannot be read as a local one is treated as remote.
    static boolean isLocal(String databaseUrl) {
        if (databaseUrl == null) {
            return false;
        }
        String url = databaseUrl.trim();
        if (url.startsWith("jdbc:")) {
            url = url.substring("jdbc:".length());
        }
        try {
            String host = new URI(url).getHost();
            return host != null && (host.equalsIgnoreCase("localhost") || host.equals("127.0.0.1"));
        } catch (Exception unreadable) {
            return false;
        }
    }
}
