package com.scantoorder.scantoorder.service.email;

/**
 * One email, in the shape every sender takes. The text version is always there; the html version comes from the template.
 * The tag names what the email is for (it shows in the mail provider's dashboard). The idempotency key, when given, makes a
 * repeat of the same send harmless: the provider delivers it once.
 */
public record EmailMessage(String to, String subject, String text, String html, String tag, String idempotencyKey) {

    public static EmailMessage of(String to, String subject, EmailTemplate.Rendered rendered, String tag, String idempotencyKey) {
        return new EmailMessage(to, subject, rendered.text(), rendered.html(), tag, idempotencyKey);
    }
}
