package com.saasbasics.platform.common.mask;

public final class DataMaskUtils {

    private DataMaskUtils() {
    }

    public static String mask(String source, DataMaskType type, int prefixKeep, int suffixKeep, char maskChar) {
        if (source == null || source.isBlank()) {
            return source;
        }
        return switch (type) {
            case MOBILE -> maskMobile(source);
            case ID_CARD -> maskIdCard(source);
            case EMAIL -> maskEmail(source);
            case BANK_CARD -> maskBankCard(source);
            case CHINESE_NAME -> maskChineseName(source);
            case CUSTOM -> maskCustom(source, prefixKeep, suffixKeep, maskChar);
        };
    }

    public static String maskMobile(String mobile) {
        if (mobile == null || mobile.length() < 7) {
            return mobile;
        }
        return mobile.replaceAll("(\\d{3})\\d{4}(\\d{4})", "$1****$2");
    }

    public static String maskIdCard(String idCard) {
        if (idCard == null || idCard.length() < 10) {
            return idCard;
        }
        return idCard.replaceAll("(\\d{6})\\d+(\\w{4})", "$1********$2");
    }

    public static String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return email;
        }
        int atIndex = email.indexOf("@");
        String name = email.substring(0, atIndex);
        String domain = email.substring(atIndex);
        if (name.length() <= 2) {
            return name.charAt(0) + "***" + domain;
        }
        return name.charAt(0) + "***" + name.charAt(name.length() - 1) + domain;
    }

    public static String maskBankCard(String card) {
        if (card == null || card.length() < 10) {
            return card;
        }
        return card.replaceAll("(\\d{6})\\d+(\\d{4})", "$1******$2");
    }

    public static String maskChineseName(String name) {
        if (name == null || name.isBlank()) {
            return name;
        }
        if (name.length() <= 1) {
            return name;
        }
        if (name.length() == 2) {
            return name.charAt(0) + "*";
        }
        return name.charAt(0) + "*".repeat(name.length() - 2) + name.charAt(name.length() - 1);
    }

    public static String maskCustom(String source, int prefixKeep, int suffixKeep, char maskChar) {
        int length = source.length();
        if (prefixKeep + suffixKeep >= length) {
            return source;
        }
        String prefix = source.substring(0, prefixKeep);
        String suffix = source.substring(length - suffixKeep);
        int maskLength = length - prefixKeep - suffixKeep;
        return prefix + String.valueOf(maskChar).repeat(maskLength) + suffix;
    }
}
