package com.saasbasics.platform.modules.iam.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saasbasics.platform.modules.iam.dto.UserPreferencesData;
import com.saasbasics.platform.modules.iam.entity.UserEntity;

/**
 * Shared serializer/deserializer helper for cloud-synced user workspace preferences.
 */
final class UserPreferencesHelper {

    private UserPreferencesHelper() {
    }

    static UserPreferencesData parse(ObjectMapper objectMapper, UserEntity user) {
        if (user == null) {
            return UserPreferencesData.defaults("zh-CN", "Asia/Shanghai");
        }
        String locale = normalizeLocale(user.getLocale());
        String timezone = normalizeTimezone(user.getTimezone());
        UserPreferencesData defaults = UserPreferencesData.defaults(locale, timezone);
        String raw = user.getPreferencesJson();
        if (raw == null || raw.isBlank() || objectMapper == null) {
            return defaults;
        }
        try {
            UserPreferencesData parsed = objectMapper.readValue(raw, UserPreferencesData.class);
            return new UserPreferencesData(
                    normalizeLocale(parsed.locale() != null ? parsed.locale() : locale),
                    normalizeTimezone(parsed.timezone() != null ? parsed.timezone() : timezone),
                    normalizeLayoutMode(parsed.layoutMode()),
                    normalizeRailTheme(parsed.railTheme()),
                    parsed.showBreadcrumbs() != null ? parsed.showBreadcrumbs() : defaults.showBreadcrumbs(),
                    parsed.sidebarAccordion() != null ? parsed.sidebarAccordion() : defaults.sidebarAccordion(),
                    parsed.enableWatermark() != null ? parsed.enableWatermark() : defaults.enableWatermark(),
                    parsed.emailNotify() != null ? parsed.emailNotify() : defaults.emailNotify(),
                    parsed.siteNotify() != null ? parsed.siteNotify() : defaults.siteNotify(),
                    parsed.securityAlertNotify() != null ? parsed.securityAlertNotify() : defaults.securityAlertNotify(),
                    parsed.defaultAiModel() != null ? parsed.defaultAiModel() : defaults.defaultAiModel()
            );
        } catch (Exception ignored) {
            return defaults;
        }
    }

    static String serialize(ObjectMapper objectMapper, UserPreferencesData data) {
        if (objectMapper == null || data == null) {
            return null;
        }
        try {
            UserPreferencesData normalized = new UserPreferencesData(
                    normalizeLocale(data.locale()),
                    normalizeTimezone(data.timezone()),
                    normalizeLayoutMode(data.layoutMode()),
                    normalizeRailTheme(data.railTheme()),
                    Boolean.TRUE.equals(data.showBreadcrumbs()),
                    Boolean.TRUE.equals(data.sidebarAccordion()),
                    Boolean.TRUE.equals(data.enableWatermark()),
                    Boolean.TRUE.equals(data.emailNotify()),
                    Boolean.TRUE.equals(data.siteNotify()),
                    Boolean.TRUE.equals(data.securityAlertNotify()),
                    data.defaultAiModel() != null ? data.defaultAiModel().trim() : ""
            );
            return objectMapper.writeValueAsString(normalized);
        } catch (Exception ignored) {
            return null;
        }
    }

    static String normalizeLocale(String raw) {
        return "en-US".equalsIgnoreCase(raw) ? "en-US" : "zh-CN";
    }

    static String normalizeTimezone(String raw) {
        return (raw != null && !raw.isBlank()) ? raw.trim() : "Asia/Shanghai";
    }

    static String normalizeLayoutMode(String raw) {
        if ("mix".equalsIgnoreCase(raw)) return "mix";
        if ("side".equalsIgnoreCase(raw)) return "side";
        return "top";
    }

    static String normalizeRailTheme(String raw) {
        return "dark".equalsIgnoreCase(raw) ? "dark" : "light";
    }
}
