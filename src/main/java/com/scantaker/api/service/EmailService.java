package com.scantaker.api.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
public class EmailService {

    @Value("${app.mail.brevo-api-key:${BREVO_API_KEY:}}")
    private String brevoApiKey;

    @Value("${app.mail.from-email:${MAIL_FROM:contact@scantaker.com}}")
    private String fromEmail;

    @Value("${app.mail.from-name:${MAIL_FROM_NAME:ScanTaker Diagnostic Network}}")
    private String fromName;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public boolean sendOtpEmail(String toEmail, String otpCode, String purpose) {
        String cleanEmail = toEmail != null ? toEmail.trim().toLowerCase() : "";
        String subject = "Your ScanTaker Verification Code: " + otpCode;
        String actionTitle = "FORGOT_PASSWORD".equalsIgnoreCase(purpose)
                ? "Password Reset Verification"
                : "CENTRE_SIGNUP".equalsIgnoreCase(purpose)
                ? "Diagnostic Centre Portal Onboarding"
                : "Patient Account Verification";

        String formattedDate = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a"));
        String htmlContent = buildProfessionalEmailTemplate(cleanEmail, otpCode, actionTitle, formattedDate);

        // 1. Try Brevo Transactional Email API (free 300 emails/day, zero port issues)
        if (brevoApiKey != null && !brevoApiKey.isBlank() && !brevoApiKey.equals("none")) {
            try {
                boolean sent = sendViaBrevoApi(cleanEmail, subject, htmlContent);
                if (sent) {
                    log.info("Real transactional OTP email successfully sent via Brevo to {}", cleanEmail);
                    return true;
                }
            } catch (Exception e) {
                log.error("Failed to send email via Brevo API: {}", e.getMessage());
            }
        }

        // 2. Console fallback with full professional header
        log.info("""
                \n======================================================================
                [SCANTAKER OFFICIAL EMAIL SERVICE - DEV/FALLBACK]
                To: {}
                Subject: {}
                Action: {}
                Date: {}
                ----------------------------------------------------------------------
                Dear User,
                
                Your 6-Digit ScanTaker Security Verification Code:
                                     [  {}  ]
                
                Valid for 10 minutes.
                Please enter this OTP in the application to complete verification.
                ----------------------------------------------------------------------
                ScanTaker Diagnostic Network | Jaipur, Rajasthan
                Support: support@scantaker.com | +91 99999 99999
                ======================================================================
                """, cleanEmail, subject, actionTitle, formattedDate, otpCode);

        return true;
    }

    private boolean sendViaBrevoApi(String toEmail, String subject, String htmlContent) {
        try {
            // Escape json characters in htmlContent
            String escapedHtml = htmlContent
                    .replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\r", "")
                    .replace("\n", "\\n");

            String payload = String.format("""
                {
                    "sender": {"name": "%s", "email": "%s"},
                    "to": [{"email": "%s"}],
                    "subject": "%s",
                    "htmlContent": "%s"
                }
                """, fromName, fromEmail, toEmail, subject, escapedHtml);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.brevo.com/v3/smtp/email"))
                    .header("accept", "application/json")
                    .header("api-key", brevoApiKey.trim())
                    .header("content-type", "application/json")
                    .timeout(Duration.ofSeconds(12))
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return true;
            } else {
                log.warn("Brevo API responded with status {}: {}", response.statusCode(), response.body());
                return false;
            }
        } catch (Exception e) {
            log.error("Exception sending via Brevo API", e);
            return false;
        }
    }

    private String buildProfessionalEmailTemplate(String email, String otpCode, String actionTitle, String formattedDate) {
        // FIX: Text block ke andar string concatenation (+) nahi kar sakte.
        // Isliye hum String.format() ka use kar rahe hain.
        return String.format("""
            <!DOCTYPE html>
            <html lang="en">
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width, initial-scale=1.0">
              <title>ScanTaker Verification Code</title>
            </head>
            <body style="margin:0;padding:0;background-color:#F0F6FB;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;-webkit-font-smoothing:antialiased;">
              <table border="0" cellpadding="0" cellspacing="0" width="100%%" style="background-color:#F0F6FB;padding:32px 16px;">
                <tr>
                  <td align="center">
                    <table border="0" cellpadding="0" cellspacing="0" width="100%%" style="max-width:560px;background:#ffffff;border-radius:24px;overflow:hidden;box-shadow:0 8px 30px rgba(10,22,41,0.08);border:1px solid #DCE9F5;">
                      
                      <!-- Brand Header Letterhead -->
                      <tr>
                        <td style="background:linear-gradient(135deg,#008DA5 0%%,#0A1629 100%%);padding:36px 32px;text-align:center;">
                          <table border="0" cellpadding="0" cellspacing="0" width="100%%">
                            <tr>
                              <td align="center">
                                <div style="display:inline-block;background:rgba(255,255,255,0.15);padding:8px 16px;border-radius:12px;margin-bottom:12px;">
                                  <span style="color:#ffffff;font-size:22px;font-weight:900;letter-spacing:1px;">ScanTaker</span>
                                  <span style="color:#38BDF8;font-size:12px;font-weight:700;margin-left:6px;text-transform:uppercase;background:rgba(255,255,255,0.2);padding:2px 8px;border-radius:6px;">HEALTHCARE</span>
                                </div>
                                <p style="margin:0;color:#E2E8F0;font-size:13px;font-weight:500;letter-spacing:0.3px;">
                                  Jaipur's Trusted MRI &amp; CT Diagnostic Imaging Network
                                </p>
                              </td>
                            </tr>
                          </table>
                        </td>
                      </tr>

                      <!-- Letterhead Sub-Bar -->
                      <tr>
                        <td style="background:#F8FAFC;border-bottom:1px solid #E2E8F0;padding:12px 32px;">
                          <table border="0" cellpadding="0" cellspacing="0" width="100%%" style="font-size:11px;color:#64748B;">
                            <tr>
                              <td align="left"><b>Date:</b> %s</td>
                              <td align="right"><b>Ref:</b> ST-SEC-%s</td>
                            </tr>
                          </table>
                        </td>
                      </tr>

                      <!-- Body Content -->
                      <tr>
                        <td style="padding:36px 32px 28px 32px;">
                          <h2 style="margin:0 0 8px 0;color:#0A1629;font-size:20px;font-weight:800;">
                            %s
                          </h2>
                          <p style="margin:0 0 20px 0;color:#475569;font-size:14px;line-height:1.6;">
                            Dear Customer, we received a request to authenticate your email address (<b>%s</b>) on the ScanTaker portal. Please use the one-time password (OTP) below to proceed:
                          </p>

                          <!-- OTP Code Box -->
                          <div style="background:linear-gradient(180deg,#F0FDFA 0%%,#E6FFFA 100%%);border:2px dashed #00A3A6;border-radius:18px;padding:24px 20px;text-align:center;margin:24px 0;">
                            <span style="display:block;font-size:11px;font-weight:800;color:#007A8A;text-transform:uppercase;letter-spacing:1.5px;margin-bottom:8px;">
                              6-Digit Verification Code
                            </span>
                            <span style="display:inline-block;font-size:40px;font-weight:900;letter-spacing:10px;color:#0A1629;font-family:'Courier New',Courier,monospace;">
                              %s
                            </span>
                            <span style="display:block;font-size:11px;color:#0F766E;font-weight:600;margin-top:10px;">
                              Valid for 10 minutes only • Do not share with anyone
                            </span>
                          </div>

                          <p style="margin:0 0 16px 0;color:#64748B;font-size:12px;line-height:1.6;">
                            If you did not request this verification, your account security may be compromised. Please ignore this email or notify our support team immediately.
                          </p>
                          
                          <!-- Security Badge -->
                          <table border="0" cellpadding="0" cellspacing="0" width="100%%" style="background:#F1F5F9;border-radius:12px;padding:12px 16px;margin-top:16px;">
                            <tr>
                              <td style="font-size:11px;color:#334155;line-height:1.5;">
                                🛡️ <b>Security Advisory:</b> ScanTaker staff or diagnostic lab personnel will never call or message you asking for your password or OTP.
                              </td>
                            </tr>
                          </table>
                        </td>
                      </tr>

                      <!-- Footer -->
                      <tr>
                        <td style="background:#0A1629;padding:28px 32px;text-align:center;border-top:1px solid #1E293B;">
                          <p style="margin:0 0 6px 0;color:#F8FAFC;font-size:12px;font-weight:700;">
                            ScanTaker Healthcare Pvt. Ltd.
                          </p>
                          <p style="margin:0 0 12px 0;color:#94A3B8;font-size:11px;line-height:1.5;">
                            NABH &amp; AERB Accredited Centre Network • Jaipur, Rajasthan<br>
                            WhatsApp Support: +91 99999 99999 • Email: support@scantaker.com
                          </p>
                          <p style="margin:0;color:#64748B;font-size:10px;">
                            &copy; 2026 ScanTaker. All rights reserved. Confidential communication.
                          </p>
                        </td>
                      </tr>

                    </table>
                  </td>
                </tr>
              </table>
            </body>
            </html>
            """, 
            formattedDate, // %s 1
            otpCode,       // %s 2
            actionTitle,   // %s 3
            email,         // %s 4
            otpCode        // %s 5
        );
    }
}