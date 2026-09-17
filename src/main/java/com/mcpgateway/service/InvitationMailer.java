package com.mcpgateway.service;

import com.mcpgateway.property.InvitationProperties;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * The two mails this system sends: an invitation, and a password reset.
 *
 * <p>Both are required rather than best-effort. A link that could not be delivered is not
 * recorded either — a row the person it names will never hear about is worse than a
 * refusal, because the refusal is visible and the row is not.
 *
 * <p>What makes requiring mail safe for invitations is that it is not the only way in: an
 * administrator can create an account with a password directly, which needs nothing but
 * the database. A misconfigured SMTP host therefore locks nobody out of their own
 * installation.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InvitationMailer {

    /**
     * The panel's brand, copied rather than shared.
     *
     * <p>There is no sharing it: mail clients strip a stylesheet link and most strip a
     * {@code <style>} block too, so a colour has to be written into the element that uses
     * it. Copied values drift, so they are named here rather than scattered through the
     * markup — one place to change when the panel's brand does.
     */
    private static final String BRAND = "#ea580c";
    private static final String TEXT = "#1f2329";
    private static final String MUTED = "#6b7280";
    private static final String RULE = "#e3e6ec";

    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("d MMMM yyyy, HH:mm").withZone(ZoneId.systemDefault());

    /**
     * Optional at wiring time, required at use.
     *
     * <p>Spring only builds a {@code JavaMailSender} when {@code spring.mail.host} is set,
     * so injecting it directly meant the gateway would not start at all without an SMTP
     * host. That is far worse than mail failing: the way in that needs none — an
     * administrator setting a password by hand — lives behind the same application, and a
     * service that will not boot takes the escape hatch with it.
     */
    private final ObjectProvider<JavaMailSender> mailSender;

    private final InvitationProperties properties;

    /**
     * An invitation.
     *
     * <p>Carries the link and when it stops working, and nothing else. Not the role: what
     * somebody is about to become is not a thing to learn from an inbox, and an inbox is
     * not where it should sit afterwards.
     */
    public void sendInvitation(String email, String token, Instant expiresAt) {
        send(email, "MCP Panel — you have been invited",
                "You have been invited to the MCP panel. Choose a password and you are in.",
                "Set your password",
                link("/invitations/", token),
                "The link works once, and stops working on " + WHEN.format(expiresAt) + ".");
    }

    /**
     * A password reset.
     *
     * <p>Says what to do if it was not asked for, because the person best placed to notice
     * somebody trying to get into an account is the person who owns it — and a mail that
     * only offers a button gives them nothing to do with the suspicion.
     */
    public void sendReset(String email, String token, Instant expiresAt) {
        send(email, "MCP Panel — reset your password",
                "Somebody asked to reset the password on this account. If that was not you, "
                + "you can ignore this message — the password has not changed, and this link "
                + "will stop working on its own.",
                "Choose a new password",
                link("/reset/", token),
                "The link works once, and stops working on " + WHEN.format(expiresAt) + ".");
    }


    private void send(String to, String subject, String body, String action,
                      String url, String expiry) {
        JavaMailSender sender = mailSender.getIfAvailable();

        if (sender == null || !StringUtils.hasText(properties.getFrom())) {
            throw new IllegalStateException(
                    "Mail is not configured; set SMTP_HOST and SMTP_FROM before inviting "
                    + "anybody, or add the account with a password instead");
        }

        try {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper =
                    new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());

            helper.setFrom(properties.getFrom());
            helper.setTo(to);
            helper.setSubject(subject);

            // Both parts, in this order. A client that will not render HTML shows the plain
            // one, and a link somebody has to pick out of a wall of markup is a link they
            // will mistype. The two say the same thing.
            helper.setText(plain(body, url, expiry), html(body, action, url, expiry));

            sender.send(message);
        } catch (Exception failure) {
            // Wrapped so the caller has one thing to catch, keeping the cause's words —
            // usually the only clue there is about a mail host.
            throw new IllegalStateException(
                    "The mail could not be sent: " + failure.getMessage(), failure);
        }

        // The address it went to, never the token: a log store with thirty days of
        // retention and no encryption is not a place to keep something that opens an
        // account.
        log.info("Mail sent to {}", to);
    }

    private static String plain(String body, String url, String expiry) {
        return """
                %s

                %s

                %s
                """.formatted(body, url, expiry);
    }

    /**
     * A message, rather than a card.
     *
     * <p>An earlier version framed all this in a bordered box on a grey page, which reads
     * as a notification widget that happens to have been posted. Mail is correspondence:
     * text on white, one column, a rule before the footer.
     *
     * <p>Tables and inline attributes rather than a stylesheet and classes — not nostalgia,
     * but the only layout the older clients agree on. Web fonts cannot be loaded, so the
     * stack falls back to whatever the reader already has. 600px because that is the width
     * mail clients were built around.
     */
    private static String html(String body, String action, String url, String expiry) {
        return """
                <!doctype html>
                <html>
                  <body style="margin:0;padding:0;background:#ffffff;color:%s;
                               font-family:system-ui,-apple-system,'Segoe UI',Roboto,sans-serif;
                               font-size:15px;line-height:1.6;">
                    <table role="presentation" width="100%%" cellpadding="0" cellspacing="0">
                      <tr>
                        <td align="center" style="padding:32px 16px;">
                          <table role="presentation" width="100%%" cellpadding="0" cellspacing="0"
                                 style="max-width:600px;text-align:left;">
                            <tr>
                              <td style="padding-bottom:20px;font-size:16px;font-weight:600;
                                         color:%s;">
                                MCP Panel
                              </td>
                            </tr>
                            <tr>
                              <td style="padding-bottom:24px;">%s</td>
                            </tr>
                            <tr>
                              <td style="padding-bottom:24px;">
                                <a href="%s"
                                   style="display:inline-block;background:%s;color:#ffffff;
                                          text-decoration:none;font-size:15px;font-weight:600;
                                          padding:12px 22px;border-radius:8px;">%s</a>
                              </td>
                            </tr>
                            <tr>
                              <td style="padding-bottom:24px;font-size:13px;color:%s;">%s</td>
                            </tr>
                            <tr>
                              <td style="border-top:1px solid %s;padding-top:16px;
                                         font-size:12px;color:%s;word-break:break-all;">
                                If the button does nothing, paste this into your browser:<br>%s
                              </td>
                            </tr>
                          </table>
                        </td>
                      </tr>
                    </table>
                  </body>
                </html>
                """.formatted(TEXT, BRAND, body, url, BRAND, action, MUTED, expiry,
                RULE, MUTED, url);
    }

    private String link(String path, String token) {
        String base = properties.getPanelUrl();
        return (base.endsWith("/") ? base.substring(0, base.length() - 1) : base) + path + token;
    }
}
