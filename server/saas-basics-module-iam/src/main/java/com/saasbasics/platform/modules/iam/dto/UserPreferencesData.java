package com.saasbasics.platform.modules.iam.dto;

public record UserPreferencesData(
        String locale,
        String timezone,
        String layoutMode,
        String railTheme,
        Boolean showBreadcrumbs,
        Boolean sidebarAccordion,
        Boolean enableWatermark,
        Boolean emailNotify,
        Boolean siteNotify,
        Boolean securityAlertNotify,
        String defaultAiModel
) {
    public static UserPreferencesData defaults(String locale, String timezone) {
        return new UserPreferencesData(
                locale != null && !locale.isBlank() ? locale : "zh-CN",
                timezone != null && !timezone.isBlank() ? timezone : "Asia/Shanghai",
                "top",
                "light",
                true,
                false,
                false,
                true,
                true,
                true,
                ""
        );
    }
}
