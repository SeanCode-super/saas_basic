package com.saasbasics.platform.modules.integration.message;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * 邮件发送请求 DTO。
 */
public record MailSendRequest(
        @NotBlank(message = "收件人邮箱不能为空") @Email(message = "邮箱格式不合法") String to,
        @NotBlank(message = "邮件主题不能为空") String subject,
        @NotBlank(message = "邮件正文不能为空") String content,
        Boolean isHtml,
        String cc
) {}
