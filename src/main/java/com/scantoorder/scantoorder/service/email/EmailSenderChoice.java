package com.scantoorder.scantoorder.service.email;

/**
 * Which sender runs, from settings alone. Each expression holds for exactly one combination, so one sender bean exists whatever is set:
 * a SendByte key wins, then an SMTP host, then the in-memory outbox.
 */
public final class EmailSenderChoice {
    public static final String SENDBYTE = "!'${SENDBYTE_API_KEY:}'.trim().isEmpty()";
    public static final String SMTP = "'${SENDBYTE_API_KEY:}'.trim().isEmpty() && !'${MAIL_HOST:}'.trim().isEmpty()";
    public static final String IN_MEMORY = "'${SENDBYTE_API_KEY:}'.trim().isEmpty() && '${MAIL_HOST:}'.trim().isEmpty()";

    private EmailSenderChoice() {
    }
}
