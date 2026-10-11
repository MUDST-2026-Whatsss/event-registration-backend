package MUDST_2026_Whatsss.event_registration.auth.service;

import MUDST_2026_Whatsss.event_registration.auth.config.AuthProperties;
import MUDST_2026_Whatsss.event_registration.auth.config.MailProperties;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.util.HtmlUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.time.Year;

/** Sends password-reset links after the token transaction has committed. */
@Service
public class PasswordResetMailService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetMailService.class);

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final MailProperties properties;
    private final AuthProperties authProperties;

    public PasswordResetMailService(ObjectProvider<JavaMailSender> mailSenderProvider,
                                    MailProperties properties,
                                    AuthProperties authProperties) {
        this.mailSenderProvider = mailSenderProvider;
        this.properties = properties;
        this.authProperties = authProperties;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void sendPasswordReset(PasswordResetRequestedEvent event) {
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null
                || properties.getFromEmail() == null
                || properties.getFromEmail().isBlank()) {
            log.error("Password reset email not sent for user {}: SMTP is not configured",
                    event.userId());
            return;
        }

        String baseUrl = properties.getFrontendBaseUrl().replaceFirst("/+$", "");
        String resetUrl = UriComponentsBuilder.fromUriString(baseUrl)
                .path("/reset-password")
                .queryParam("token", event.rawToken())
                .build()
                .encode()
                .toUriString();

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    message, true, StandardCharsets.UTF_8.name());
            if (properties.getFromName() == null || properties.getFromName().isBlank()) {
                helper.setFrom(properties.getFromEmail());
            } else {
                helper.setFrom(properties.getFromEmail(), properties.getFromName());
            }
            helper.setTo(event.email());
            helper.setSubject("Reset your password | Eventsss");
            helper.setText(buildPlainText(resetUrl), buildHtml(resetUrl));
            mailSender.send(message);
            log.info("Password reset email sent for user {}", event.userId());
        } catch (MessagingException | MailException | UnsupportedEncodingException ex) {
            log.error("Unable to send password reset email for user {}", event.userId(), ex);
        }
    }

    private String buildPlainText(String resetUrl) {
        return """
                Eventsss

                Reset your password

                We received a request to reset the password for your Eventsss account.

                Reset your password:
                %s

                This link expires in %s.

                If you did not request this, you can safely ignore this email. Your password will not change.

                This is an automated email. Please do not reply.
                """.formatted(resetUrl, expiryLabel());
    }

    private String buildHtml(String resetUrl) {
        String safeResetUrl = HtmlUtils.htmlEscape(resetUrl);
        return """
                <!doctype html>
                <html lang="en">
                  <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>Reset your Eventsss password</title>
                  </head>
                  <body style="margin:0;padding:0;background-color:#f5f5fb;color:#171925;font-family:Arial,Helvetica,sans-serif;">
                    <div style="display:none;max-height:0;overflow:hidden;opacity:0;">
                      Reset your Eventsss password securely.
                    </div>
                    <table role="presentation" width="100%" cellspacing="0" cellpadding="0" border="0" style="background-color:#f5f5fb;">
                      <tr>
                        <td align="center" style="padding:40px 16px;">
                          <table role="presentation" width="100%" cellspacing="0" cellpadding="0" border="0" style="max-width:560px;background-color:#ffffff;border:1px solid #e6e7f0;border-radius:16px;overflow:hidden;">
                            <tr>
                              <td style="padding:28px 36px 22px;border-bottom:1px solid #ececf4;">
                                <table role="presentation" cellspacing="0" cellpadding="0" border="0">
                                  <tr>
                                    <td width="42" height="42" align="center" style="width:42px;height:42px;border-radius:12px;background-color:#5b55f7;color:#ffffff;font-size:20px;font-weight:700;">E</td>
                                    <td style="padding-left:12px;color:#171925;font-size:21px;font-weight:700;letter-spacing:-0.3px;">Eventsss</td>
                                  </tr>
                                </table>
                              </td>
                            </tr>
                            <tr>
                              <td style="padding:34px 36px 38px;">
                                <h1 style="margin:0 0 14px;color:#171925;font-size:28px;line-height:1.25;letter-spacing:-0.5px;">Reset your password</h1>
                                <p style="margin:0 0 26px;color:#646b80;font-size:16px;line-height:1.6;">We received a request to reset the password for your Eventsss account.</p>
                                <table role="presentation" cellspacing="0" cellpadding="0" border="0" style="margin:0 0 24px;">
                                  <tr>
                                    <td align="center" bgcolor="#5b55f7" style="border-radius:8px;">
                                      <a href="${RESET_URL}" style="display:inline-block;padding:14px 26px;color:#ffffff;font-size:16px;font-weight:700;text-decoration:none;">Reset password</a>
                                    </td>
                                  </tr>
                                </table>
                                <p style="margin:0 0 22px;color:#646b80;font-size:14px;line-height:1.6;">This link expires in <strong style="color:#34384a;">${EXPIRY}</strong>.</p>
                                <div style="margin:0 0 24px;padding:16px;background-color:#f7f7fc;border-radius:10px;">
                                  <p style="margin:0 0 7px;color:#646b80;font-size:13px;line-height:1.5;">If the button does not work, copy and paste this URL into your browser:</p>
                                  <a href="${RESET_URL}" style="color:#4d48d9;font-size:13px;line-height:1.5;text-decoration:underline;word-break:break-all;">${RESET_URL}</a>
                                </div>
                                <p style="margin:0;color:#646b80;font-size:14px;line-height:1.6;">If you did not request this, you can safely ignore this email. Your password will not change.</p>
                              </td>
                            </tr>
                            <tr>
                              <td style="padding:20px 36px;background-color:#fafafd;border-top:1px solid #ececf4;color:#8b91a3;font-size:12px;line-height:1.5;">
                                © ${YEAR} Eventsss · This is an automated email. Please do not reply.
                              </td>
                            </tr>
                          </table>
                        </td>
                      </tr>
                    </table>
                  </body>
                </html>
                """
                .replace("${RESET_URL}", safeResetUrl)
                .replace("${EXPIRY}", expiryLabel())
                .replace("${YEAR}", Integer.toString(Year.now().getValue()));
    }

    private String expiryLabel() {
        long minutes = Math.max(1, authProperties.getPasswordResetTtl().toMinutes());
        return minutes == 1 ? "1 minute" : minutes + " minutes";
    }
}
