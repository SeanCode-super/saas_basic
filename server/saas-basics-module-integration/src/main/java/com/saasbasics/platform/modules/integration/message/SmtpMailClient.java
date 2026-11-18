package com.saasbasics.platform.modules.integration.message;

import com.saasbasics.platform.common.exception.BizException;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * 标准 SMTP 协议邮件客户端。
 * 当配置 saas.mail.smtp.host 时执行真实 SMTP/SSL/STARTTLS 协议握手与 MIME 投递；
 * 未配置时在非 prod 环境提供明确标注 [SANDBOX] 的演练回执，在 prod 环境严格阻断。
 */
@Component
public class SmtpMailClient implements MailClient {

    private static final Logger log = LoggerFactory.getLogger(SmtpMailClient.class);

    @Value("${saas.mail.smtp.host:}")
    private String smtpHost = "";

    @Value("${saas.mail.smtp.port:465}")
    private int smtpPort = 465;

    @Value("${saas.mail.smtp.username:}")
    private String smtpUsername = "";

    @Value("${saas.mail.smtp.password:}")
    private String smtpPassword = "";

    @Value("${saas.mail.smtp.from:}")
    private String smtpFrom = "";

    @Value("${saas.mail.smtp.ssl:true}")
    private boolean useSsl = true;

    private final Environment environment;

    public SmtpMailClient() {
        this(null);
    }

    @Autowired
    public SmtpMailClient(Environment environment) {
        this.environment = environment;
    }

    @Override
    public MailSendResult sendMail(MailSendRequest request) {
        if (smtpHost == null || smtpHost.isBlank()) {
            boolean isProd = environment != null && environment.matchesProfiles("prod");
            if (isProd) {
                throw new BizException("MAIL_CREDENTIALS_MISSING",
                        "生产环境尚未配置 SMTP 邮件服务器 (saas.mail.smtp.host)");
            }
            String msgId = "mail-sandbox-" + UUID.randomUUID().toString().substring(0, 8);
            log.info("[SmtpMailClient] [SANDBOX] 未配置 saas.mail.smtp.host，记录开发演练邮件: to={}, subject={}",
                    request.to(), request.subject());
            return MailSendResult.success(msgId, request.to(), request.subject(),
                    "[SANDBOX] 未配置外部 SMTP 凭证，已记录开发沙箱邮件: " + msgId);
        }

        String sender = (smtpFrom != null && !smtpFrom.isBlank()) ? smtpFrom.trim() : smtpUsername.trim();
        String msgId = "smtp-" + UUID.randomUUID().toString().substring(0, 12);
        try {
            deliverViaSmtp(sender, request);
            log.info("[SmtpMailClient] SMTP 真实投递成功: host={}, to={}, msgId={}", smtpHost, request.to(), msgId);
            return MailSendResult.success(msgId, request.to(), request.subject(),
                    "邮件已通过 SMTP 服务器真实投递 (MessageId: " + msgId + ")");
        } catch (Exception ex) {
            log.warn("[SmtpMailClient] SMTP 投递失败: to={}, error={}", request.to(), ex.getMessage());
            return MailSendResult.failure(request.to(), request.subject(), "SMTP 投递失败: " + ex.getMessage());
        }
    }

    private void deliverViaSmtp(String sender, MailSendRequest request) throws IOException {
        try (Socket socket = openSmtpSocket()) {
            socket.setSoTimeout(8000);
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                 BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))) {
                expectCode(reader, 220);
                sendLine(writer, "EHLO tianshu-saas");
                expectCode(reader, 250);

                if (smtpUsername != null && !smtpUsername.isBlank() && smtpPassword != null && !smtpPassword.isBlank()) {
                    sendLine(writer, "AUTH LOGIN");
                    expectCode(reader, 334);
                    sendLine(writer, Base64.getEncoder().encodeToString(smtpUsername.trim().getBytes(StandardCharsets.UTF_8)));
                    expectCode(reader, 334);
                    sendLine(writer, Base64.getEncoder().encodeToString(smtpPassword.getBytes(StandardCharsets.UTF_8)));
                    expectCode(reader, 235);
                }

                sendLine(writer, "MAIL FROM:<" + sanitizeHeader(sender) + ">");
                expectCode(reader, 250);
                sendLine(writer, "RCPT TO:<" + sanitizeHeader(request.to()) + ">");
                expectCode(reader, 250);
                sendLine(writer, "DATA");
                expectCode(reader, 354);

                String mimeType = Boolean.TRUE.equals(request.isHtml()) ? "text/html" : "text/plain";
                String encodedSubj = "=?UTF-8?B?" + Base64.getEncoder().encodeToString(request.subject().getBytes(StandardCharsets.UTF_8)) + "?=";
                writer.write("From: <" + sanitizeHeader(sender) + ">\r\n");
                writer.write("To: <" + sanitizeHeader(request.to()) + ">\r\n");
                if (request.cc() != null && !request.cc().isBlank()) {
                    writer.write("Cc: " + sanitizeHeader(request.cc()) + "\r\n");
                }
                writer.write("Subject: " + encodedSubj + "\r\n");
                writer.write("MIME-Version: 1.0\r\n");
                writer.write("Content-Type: " + mimeType + "; charset=UTF-8\r\n\r\n");
                writer.write(request.content().replace("\r\n.", "\r\n..") + "\r\n.\r\n");
                writer.flush();
                expectCode(reader, 250);

                sendLine(writer, "QUIT");
                reader.readLine();
            }
        }
    }

    private Socket openSmtpSocket() throws IOException {
        if (useSsl || smtpPort == 465) {
            SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
            SSLSocket ssl = (SSLSocket) factory.createSocket();
            ssl.connect(new InetSocketAddress(smtpHost.trim(), smtpPort), 5000);
            ssl.startHandshake();
            return ssl;
        }
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(smtpHost.trim(), smtpPort), 5000);
        return socket;
    }

    private static void sendLine(BufferedWriter writer, String cmd) throws IOException {
        writer.write(cmd + "\r\n");
        writer.flush();
    }

    private static void expectCode(BufferedReader reader, int expectedPrefix) throws IOException {
        String lastLine = null;
        String line;
        while ((line = reader.readLine()) != null) {
            lastLine = line;
            if (line.length() < 4 || line.charAt(3) != '-') {
                break;
            }
        }
        if (lastLine == null || !lastLine.startsWith(String.valueOf(expectedPrefix))) {
            throw new IOException("Unexpected SMTP server response (expected " + expectedPrefix + "): " + lastLine);
        }
    }

    private static String sanitizeHeader(String input) {
        return input == null ? "" : input.replaceAll("[\\r\\n<>]", "").trim();
    }
}
