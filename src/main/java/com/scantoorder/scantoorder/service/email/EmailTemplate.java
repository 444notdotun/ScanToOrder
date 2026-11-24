package com.scantoorder.scantoorder.service.email;

import org.springframework.web.util.HtmlUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * The one look for every Plately email, drawn twice from the same words: an html version (single column, 600px, tables and inline styles only,
 * because mail apps drop style sheets) and a plain text version. Every value that came from a person is escaped before it reaches the html.
 * The only picture is the logo in the header; its alt text keeps the header reading "Plately" when images are blocked.
 */
public final class EmailTemplate {

    static final String LOGO_URL = "https://www.platelyy.com/email/plately-logo.png";
    static final String SUPPORT = "support@platelyy.com";

    private static final String GROUND = "#E9E2D2";
    private static final String CARD = "#FBF7EF";
    private static final String DEEP = "#10281F";
    private static final String GOLD = "#F0B94A";
    private static final String BODY = "#183A2E";
    private static final String EYEBROW = "#6B5E3E";
    private static final String BOX = "#F3ECDC";
    private static final String LINE = "#E4DAC4";
    private static final String MUTED = "#4A4A3E";
    private static final String SANS = "Helvetica, Arial, sans-serif";
    private static final String SERIF = "Georgia, serif";

    // Gmail repaints background-color in dark mode but leaves background-image alone, so the colours that must hold are painted twice.
    private static String gradient(String colour) {
        return "background-image:linear-gradient(" + colour + "," + colour + ");";
    }

    // The dark look for apps that honour it: the media query, and Outlook's [data-ogsc] (text) and [data-ogsb] (background) markers.
    // The header and the gold stay as they are; the inline styles stay the light email for apps that drop style blocks.
    private static final String DARK_CSS = ":root{color-scheme:light dark;supported-color-schemes:light dark;}"
            + "@media (prefers-color-scheme: dark){"
            + darkRules("", "")
            + "}"
            + darkRules("[data-ogsb] ", "[data-ogsc] ");

    private static String darkRules(String back, String text) {
        return back + ".pg-ground{background-color:#0F1C16 !important;}"
                + back + ".pg-card{background-color:#16261F !important;}"
                + back + ".pg-box{background-color:#1E3229 !important;}"
                + back + ".pg-header{background-color:#10281F !important;}"
                + back + ".pg-btncell{background-color:#F0B94A !important;}"
                + text + ".pg-text{color:#F4ECD8 !important;}"
                + text + ".pg-head{color:#F4ECD8 !important;}"
                + text + ".pg-eyebrow{color:#E8C77A !important;}"
                + text + ".pg-small{color:#C9D3C4 !important;}"
                + text + ".pg-line{border-color:#2B4237 !important;}"
                + text + ".pg-link{color:#F0B94A !important;}"
                + text + ".pg-btn{color:#10281F !important;}";
    }

    public record Row(String label, String value) {
    }

    public record Rendered(String html, String text) {
    }

    private interface Block {
        void html(StringBuilder out);

        void text(StringBuilder out);
    }

    private final String eyebrow;
    private final String heading;
    private final List<Block> blocks = new ArrayList<>();
    private boolean platformOnly;

    private EmailTemplate(String eyebrow, String heading) {
        this.eyebrow = eyebrow;
        this.heading = heading;
    }

    public static EmailTemplate of(String eyebrow, String heading) {
        return new EmailTemplate(eyebrow, heading);
    }

    // A paragraph of body text.
    public EmailTemplate paragraph(String text) {
        blocks.add(new Block() {
            public void html(StringBuilder out) {
                out.append("<p class=\"pg-text\" style=\"margin:0 0 16px;font-family:").append(SANS).append(";font-size:16px;line-height:1.6;color:").append(BODY).append(";\">")
                        .append(esc(text)).append("</p>");
            }

            public void text(StringBuilder out) {
                out.append(text).append("\n\n");
            }
        });
        return this;
    }

    // Label on the left, value in bold on the right, on a tinted box.
    public EmailTemplate details(List<Row> rows) {
        blocks.add(new Block() {
            public void html(StringBuilder out) {
                out.append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" style=\"margin:8px 0 24px;\"><tr><td class=\"pg-box\" style=\"background:")
                        .append(BOX).append(";border-radius:12px;padding:12px 20px;\"><table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\">");
                for (Row row : rows) {
                    out.append("<tr><td class=\"pg-small\" style=\"padding:6px 0;font-family:").append(SANS).append(";font-size:15px;line-height:1.5;color:").append(MUTED).append(";\">")
                            .append(esc(row.label())).append("</td><td align=\"right\" class=\"pg-text\" style=\"padding:6px 0 6px 16px;font-family:").append(SANS)
                            .append(";font-size:15px;line-height:1.5;font-weight:700;color:").append(BODY).append(";\">").append(esc(row.value())).append("</td></tr>");
                }
                out.append("</table></td></tr></table>");
            }

            public void text(StringBuilder out) {
                for (Row row : rows) {
                    out.append(row.label()).append(": ").append(row.value()).append('\n');
                }
                out.append('\n');
            }
        });
        return this;
    }

    // Ruled lines (a receipt's items), then the total in the serif.
    public EmailTemplate lines(List<Row> items, String totalLabel, String total) {
        blocks.add(new Block() {
            public void html(StringBuilder out) {
                out.append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" style=\"margin:8px 0 0;\">");
                for (Row item : items) {
                    out.append("<tr><td class=\"pg-text pg-line\" style=\"padding:10px 0;border-bottom:1px solid #ECE3CF;font-family:").append(SANS).append(";font-size:15px;line-height:1.5;color:")
                            .append(BODY).append(";\">").append(esc(item.label())).append("</td><td align=\"right\" class=\"pg-text pg-line\" style=\"padding:10px 0 10px 16px;border-bottom:1px solid #ECE3CF;font-family:")
                            .append(SANS).append(";font-size:15px;line-height:1.5;font-weight:700;color:").append(BODY).append(";white-space:nowrap;\">").append(esc(item.value())).append("</td></tr>");
                }
                out.append("<tr><td class=\"pg-head\" style=\"padding:18px 0 6px;font-family:").append(SERIF).append(";font-size:22px;color:").append(DEEP).append(";\">").append(esc(totalLabel))
                        .append("</td><td align=\"right\" class=\"pg-head\" style=\"padding:18px 0 6px 16px;font-family:").append(SERIF).append(";font-size:22px;color:").append(DEEP)
                        .append(";white-space:nowrap;\">").append(esc(total)).append("</td></tr></table>");
            }

            public void text(StringBuilder out) {
                for (Row item : items) {
                    out.append(item.label()).append("  ").append(item.value()).append('\n');
                }
                out.append(totalLabel).append("  ").append(total).append("\n\n");
            }
        });
        return this;
    }

    // The gold pill, and under it the link written out for the days a button does not work.
    public EmailTemplate button(String label, String link) {
        blocks.add(new Block() {
            public void html(StringBuilder out) {
                out.append("<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" style=\"margin:12px 0 0;\"><tr><td align=\"center\" bgcolor=\"").append(GOLD)
                        .append("\" class=\"pg-btncell\" style=\"background:").append(GOLD).append(";").append(gradient(GOLD)).append("border-radius:999px;\"><a href=\"").append(attr(link)).append("\" class=\"pg-btn\" style=\"display:inline-block;padding:15px 28px;font-family:")
                        .append(SANS).append(";font-size:16px;font-weight:700;line-height:18px;color:").append(DEEP).append(";text-decoration:none;border-radius:999px;\">").append(esc(label))
                        .append("</a></td></tr></table><p class=\"pg-small\" style=\"margin:24px 0 0;font-family:").append(SANS).append(";font-size:13px;line-height:1.5;color:#5A5A4E;\">Button not working? Copy this link into your browser:</p>")
                        .append("<p class=\"pg-text\" style=\"margin:4px 0 0;font-family:").append(SANS).append(";font-size:13px;line-height:1.5;word-break:break-all;\"><a href=\"").append(attr(link))
                        .append("\" class=\"pg-link\" style=\"color:").append(BODY).append(";\">").append(esc(link)).append("</a></p>");
            }

            public void text(StringBuilder out) {
                out.append(label).append(":\n").append(link).append("\n\n");
            }
        });
        return this;
    }

    // A tinted note, such as "If you didn't ask, ignore this email."
    public EmailTemplate note(String text) {
        blocks.add(new Block() {
            public void html(StringBuilder out) {
                out.append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" style=\"margin:32px 0 0;\"><tr><td class=\"pg-box pg-small\" style=\"background:").append(BOX)
                        .append(";border-radius:10px;padding:16px 18px;font-family:").append(SANS).append(";font-size:14px;line-height:1.5;color:").append(MUTED).append(";\">")
                        .append(esc(text)).append("</td></tr></table>");
            }

            public void text(StringBuilder out) {
                out.append(text).append("\n\n");
            }
        });
        return this;
    }

    // A smaller line of text after the main content.
    public EmailTemplate small(String text) {
        blocks.add(new Block() {
            public void html(StringBuilder out) {
                out.append("<p class=\"pg-small\" style=\"margin:24px 0 0;font-family:").append(SANS).append(";font-size:14px;line-height:1.6;color:").append(MUTED).append(";\">").append(esc(text)).append("</p>");
            }

            public void text(StringBuilder out) {
                out.append(text).append("\n\n");
            }
        });
        return this;
    }

    // Alerts to the platform owner carry only "Sent to the platform owner only." in the footer.
    public EmailTemplate platformOnly() {
        this.platformOnly = true;
        return this;
    }

    public Rendered render() {
        return new Rendered(html(), text());
    }

    private String html() {
        StringBuilder out = new StringBuilder(4096);
        out.append("<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">")
                .append("<meta name=\"color-scheme\" content=\"light dark\"><meta name=\"supported-color-schemes\" content=\"light dark\">")
                .append("<style>").append(DARK_CSS).append("</style><title>")
                .append(esc(heading)).append("</title></head><body class=\"pg-ground\" style=\"margin:0;padding:0;background:").append(GROUND).append(";\">")
                .append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" bgcolor=\"").append(GROUND).append("\" class=\"pg-ground\" style=\"background:").append(GROUND)
                .append(";\"><tr><td align=\"center\" style=\"padding:32px 12px;\">")
                .append("<table role=\"presentation\" width=\"600\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" class=\"pg-card\" style=\"width:100%;max-width:600px;background:").append(CARD)
                .append(";border-radius:14px;overflow:hidden;\">");
        // The header: deep green, a 3px gold line under it, the logo as one image.
        out.append("<tr><td bgcolor=\"").append(DEEP).append("\" class=\"pg-header\" style=\"background:").append(DEEP).append(";").append(gradient(DEEP)).append("padding:22px 32px;")
                .append("\"><img src=\"").append(LOGO_URL).append("\" width=\"150\" alt=\"Plately\" style=\"display:block;border:0;width:150px;max-width:100%;height:auto;font-family:")
                .append(SERIF).append(";font-size:26px;line-height:1;color:#F4ECD8;\"></td></tr>");
        out.append("<tr><td height=\"3\" bgcolor=\"").append(GOLD).append("\" class=\"pg-btncell\" style=\"background:").append(GOLD).append(";").append(gradient(GOLD))
                .append("height:3px;line-height:3px;font-size:0;\">&nbsp;</td></tr>");
        out.append("<tr><td style=\"padding:40px 40px 36px;\">");
        out.append("<p class=\"pg-eyebrow\" style=\"margin:0 0 8px;font-family:").append(SANS).append(";font-size:13px;font-weight:700;letter-spacing:0.08em;text-transform:uppercase;color:").append(EYEBROW)
                .append(";\">").append(esc(eyebrow)).append("</p>");
        out.append("<h1 class=\"pg-head\" style=\"margin:0 0 20px;font-family:").append(SERIF).append(";font-size:32px;font-weight:400;line-height:1.2;color:").append(DEEP).append(";\">").append(esc(heading)).append("</h1>");
        for (Block block : blocks) {
            block.html(out);
        }
        out.append("</td></tr>");
        out.append("<tr><td class=\"pg-small pg-line\" style=\"padding:20px 40px 28px;border-top:1px solid ").append(LINE).append(";font-family:").append(SANS).append(";font-size:13px;line-height:1.6;color:#5A5A4E;\">");
        if (platformOnly) {
            out.append("Sent to the platform owner only.");
        } else {
            out.append("Plately &middot; Order from your table, no waiting.<br>Questions? Reply to this email or write to <a href=\"mailto:").append(SUPPORT)
                    .append("\" class=\"pg-link\" style=\"color:").append(BODY).append(";\">").append(SUPPORT).append("</a>");
        }
        out.append("</td></tr></table></td></tr></table></body></html>");
        return out.toString();
    }

    private String text() {
        StringBuilder out = new StringBuilder(1024);
        out.append(eyebrow.toUpperCase()).append("\n").append(heading).append("\n\n");
        for (Block block : blocks) {
            block.text(out);
        }
        out.append("--\n");
        if (platformOnly) {
            out.append("Sent to the platform owner only.\n");
        } else {
            out.append("Plately · Order from your table, no waiting.\nQuestions? Reply to this email or write to ").append(SUPPORT).append('\n');
        }
        return out.toString();
    }

    private static String esc(String value) {
        return HtmlUtils.htmlEscape(value == null ? "" : value, "UTF-8");
    }

    // A link in an attribute: escaped, and only ever a web address.
    private static String attr(String link) {
        String safe = link != null && (link.startsWith("https://") || link.startsWith("http://")) ? link : "#";
        return HtmlUtils.htmlEscape(safe, "UTF-8");
    }
}
