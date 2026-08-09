package com.saasbasics.platform.modules.iam.service;

import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.modules.iam.entity.PasswordPolicyEntity;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class PasswordSecurityService {

    private static final Pattern UPPERCASE = Pattern.compile(".*[A-Z].*");
    private static final Pattern LOWERCASE = Pattern.compile(".*[a-z].*");
    private static final Pattern NUMBER = Pattern.compile(".*\\d.*");
    private static final Pattern SPECIAL = Pattern.compile(".*[^A-Za-z0-9].*");

    public String hash(String rawPassword) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawPassword.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 not available", exception);
        }
    }

    public boolean matches(String rawPassword, String passwordHash) {
        if (passwordHash == null || passwordHash.isBlank()) {
            return false;
        }
        return hash(rawPassword).equalsIgnoreCase(passwordHash);
    }

    public void validate(String rawPassword, PasswordPolicyEntity policy) {
        if (policy == null) {
            return;
        }
        int minLength = policy.getMinLength() == null ? 8 : policy.getMinLength();
        int maxLength = policy.getMaxLength() == null ? 20 : policy.getMaxLength();
        if (rawPassword.length() < minLength || rawPassword.length() > maxLength) {
            throw new BizException("PASSWORD_POLICY_VIOLATION", "Password length is out of policy range");
        }
        if (Boolean.TRUE.equals(policy.getRequireUppercase()) && !UPPERCASE.matcher(rawPassword).matches()) {
            throw new BizException("PASSWORD_POLICY_VIOLATION", "Password must contain uppercase letters");
        }
        if (Boolean.TRUE.equals(policy.getRequireLowercase()) && !LOWERCASE.matcher(rawPassword).matches()) {
            throw new BizException("PASSWORD_POLICY_VIOLATION", "Password must contain lowercase letters");
        }
        if (Boolean.TRUE.equals(policy.getRequireNumber()) && !NUMBER.matcher(rawPassword).matches()) {
            throw new BizException("PASSWORD_POLICY_VIOLATION", "Password must contain numbers");
        }
        if (Boolean.TRUE.equals(policy.getRequireSpecial()) && !SPECIAL.matcher(rawPassword).matches()) {
            throw new BizException("PASSWORD_POLICY_VIOLATION", "Password must contain special characters");
        }
    }
}
