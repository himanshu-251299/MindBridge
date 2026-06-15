package com.mindbridge.service;

import com.mindbridge.model.BurnoutScore;
import com.mindbridge.model.Employee;
import com.mindbridge.model.User;
import com.mindbridge.repository.UserRepository;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * NotificationService — Sends HTML burnout alert emails to HR managers.
 * Uses plain Java string HTML — no Thymeleaf dependency needed.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final JavaMailSender mailSender;
    private final UserRepository userRepository;

    @Value("${app.mail.from}")
    private String fromEmail;

    @Value("${app.mail.from-name}")
    private String fromName;

    @Value("${app.notifications.enabled:true}")
    private boolean notificationsEnabled;

    @Async
    public void sendBurnoutAlert(BurnoutScore score, Employee employee) {
        if (!notificationsEnabled) {
            log.info("Notifications disabled — skipping alert for company {}", employee.getCompanyId());
            return;
        }

        Optional<User> hrManager = userRepository.findAll().stream()
                .filter(u -> u.getCompanyId().equals(employee.getCompanyId()))
                .filter(u -> u.getRole() == User.Role.HR_MANAGER || u.getRole() == User.Role.ADMIN)
                .filter(User::isActive)
                .findFirst();

        if (hrManager.isEmpty()) {
            log.warn("No active HR Manager found for company {} — skipping alert", employee.getCompanyId());
            return;
        }

        User hr = hrManager.get();

        try {
            String subject = buildSubject(score.getRiskLevel());
            String htmlContent = buildEmailHtml(score, hr);
            sendHtmlEmail(hr.getEmail(), subject, htmlContent);

            log.info("✅ Burnout alert sent to {} for {} risk in company {}",
                    hr.getEmail(), score.getRiskLevel(), employee.getCompanyId());

        } catch (Exception e) {
            log.error("❌ Failed to send burnout alert to {}: {}", hr.getEmail(), e.getMessage());
        }
    }

    @Async
    public void sendWeeklySummary(String hrEmail, String hrName, String companyName,
                                  int totalEmployees, double avgRiskScore,
                                  int highRiskCount, List<String> topStressors) {
        if (!notificationsEnabled) return;
        try {
            String subject = "[MindBridge] Weekly Wellness Summary — " + companyName;
            String body = """
                <html><body style="font-family:sans-serif;padding:20px;">
                <h2>📊 Weekly Wellness Summary</h2>
                <p>Hi %s, here's the summary for <strong>%s</strong>:</p>
                <ul>
                  <li>👥 Employees monitored: <strong>%d</strong></li>
                  <li>📈 Avg risk score: <strong>%.0f/100</strong></li>
                  <li>⚠️ High/Critical: <strong>%d</strong></li>
                  <li>🔍 Top stressors: <strong>%s</strong></li>
                </ul>
                </body></html>
                """.formatted(hrName, companyName, totalEmployees,
                    avgRiskScore, highRiskCount, String.join(", ", topStressors));
            sendHtmlEmail(hrEmail, subject, body);
        } catch (Exception e) {
            log.error("Failed to send weekly summary: {}", e.getMessage());
        }
    }

    // ─────────────────────────────────────
    // HTML built in Java — no template files needed
    // ─────────────────────────────────────

    private String buildEmailHtml(BurnoutScore score, User hr) {
        String headerColor  = score.getRiskLevel() == BurnoutScore.RiskLevel.CRITICAL
                ? "#dc2626" : "#ea580c";
        String badgeColor   = score.getRiskLevel() == BurnoutScore.RiskLevel.CRITICAL
                ? "#dc2626" : "#ea580c";
        String hrName       = hr.getFullName() != null ? hr.getFullName() : "HR Manager";
        String riskLevel    = score.getRiskLevel().name();
        int    riskScore    = score.getRiskScore() != null ? score.getRiskScore() : 0;
        String trend        = score.getTrendDirection() != null ? score.getTrendDirection().name() : "UNKNOWN";
        String reasoning    = score.getAiReasoning() != null ? score.getAiReasoning() : "Pattern analysis detected risk signals.";
        String stressorTags = buildStressorTags(score.getPrimaryStressors());

        return """
            <!DOCTYPE html>
            <html>
            <head><meta charset="UTF-8"/></head>
            <body style="margin:0;padding:20px;background:#f5f5f5;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;">
              <div style="max-width:600px;margin:0 auto;background:white;border-radius:12px;overflow:hidden;box-shadow:0 2px 8px rgba(0,0,0,0.1);">

                <!-- Header -->
                <div style="background:%s;padding:32px;text-align:center;">
                  <h1 style="color:white;margin:0;font-size:22px;">⚠️ Burnout Risk Alert</h1>
                  <span style="display:inline-block;margin-top:12px;padding:4px 16px;background:white;color:%s;border-radius:20px;font-weight:700;font-size:14px;">
                    %s RISK
                  </span>
                </div>

                <!-- Body -->
                <div style="padding:32px;">
                  <p style="color:#374151;">Hi <strong>%s</strong>,</p>
                  <p style="color:#374151;line-height:1.6;">
                    MindBridge has detected a <strong>%s</strong> burnout risk signal in your team.
                    This team member may need your attention.
                  </p>

                  <!-- Score Box -->
                  <div style="background:#fef3c7;border-left:4px solid #f59e0b;border-radius:6px;padding:16px 20px;margin:20px 0;">
                    <div style="font-size:36px;font-weight:800;color:#92400e;">%d<span style="font-size:18px;">/100</span></div>
                    <div style="font-size:13px;color:#78350f;margin-top:4px;">Burnout Risk Score — Trend: <strong>%s</strong></div>
                  </div>

                  <!-- Stressors -->
                  %s

                  <!-- AI Reasoning -->
                  <div style="background:#f9fafb;border-radius:8px;padding:16px 20px;margin:20px 0;">
                    <h3 style="font-size:14px;font-weight:600;color:#111827;margin:0 0 8px;">🤖 AI Analysis</h3>
                    <p style="font-size:14px;color:#6b7280;margin:0;">%s</p>
                  </div>

                  <!-- Action -->
                  <div style="background:#eff6ff;border:1px solid #bfdbfe;border-radius:8px;padding:16px 20px;margin:20px 0;">
                    <h3 style="font-size:14px;font-weight:600;color:#1e40af;margin:0 0 6px;">✅ Recommended Action</h3>
                    <p style="font-size:14px;color:#1d4ed8;margin:0;">
                      Schedule a private, supportive 1-on-1 conversation with this team member
                      to check in on their workload and wellbeing.
                    </p>
                  </div>

                  <p style="font-size:12px;color:#9ca3af;text-align:center;">
                    🔒 <strong>Privacy Notice:</strong> Employee identity is protected.
                    This alert is based on anonymized pattern analysis.
                  </p>
                </div>

                <!-- Footer -->
                <div style="background:#f9fafb;padding:20px 32px;border-top:1px solid #e5e7eb;text-align:center;">
                  <p style="font-size:12px;color:#9ca3af;margin:0;">Sent by <strong>MindBridge</strong> — AI-Powered Burnout Prevention</p>
                  <p style="font-size:12px;color:#9ca3af;margin:4px 0 0;">Alert generated on %s</p>
                </div>

              </div>
            </body>
            </html>
            """.formatted(
                headerColor, badgeColor, riskLevel,
                hrName, riskLevel,
                riskScore, trend,
                stressorTags,
                reasoning,
                LocalDate.now()
        );
    }

    private String buildStressorTags(List<String> stressors) {
        if (stressors == null || stressors.isEmpty()) return "";
        StringBuilder tags = new StringBuilder();
        tags.append("<div style='margin:20px 0;'>");
        tags.append("<h3 style='font-size:14px;font-weight:600;color:#111827;margin:0 0 10px;'>🔍 Identified Stress Signals</h3>");
        for (String s : stressors) {
            tags.append("<span style='display:inline-block;background:#fee2e2;color:#991b1b;border-radius:4px;padding:3px 10px;font-size:12px;margin:3px;'>")
                    .append(s).append("</span>");
        }
        tags.append("</div>");
        return tags.toString();
    }

    private String buildSubject(BurnoutScore.RiskLevel riskLevel) {
        return switch (riskLevel) {
            case CRITICAL -> "🚨 [MindBridge] CRITICAL Burnout Risk Detected — Immediate Action Needed";
            case HIGH     -> "⚠️ [MindBridge] HIGH Burnout Risk Alert — Team Member Needs Attention";
            default       -> "[MindBridge] Burnout Risk Alert";
        };
    }

    private void sendHtmlEmail(String to, String subject, String htmlContent)
            throws MessagingException, UnsupportedEncodingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setFrom(fromEmail, fromName);
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(htmlContent, true);
        mailSender.send(message);
    }
}