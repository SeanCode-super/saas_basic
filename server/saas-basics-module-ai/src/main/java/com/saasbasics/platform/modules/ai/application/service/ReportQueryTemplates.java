package com.saasbasics.platform.modules.ai.application.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Objects;
import java.util.Properties;

/** Produces candidate SQL for the same validator and permission checks as a model-generated query. */
final class ReportQueryTemplates {
    private static final Properties REPORTS = load();

    private ReportQueryTemplates() {}

    static String render(String report, long tenantId, int maxRows) {
        return Objects.requireNonNull(REPORTS.getProperty(report), report)
                .replace("{tenantId}", Long.toString(tenantId))
                .replace("{maxRows}", Integer.toString(maxRows));
    }

    static String customers(long tenantId, int maxRows, boolean onlyPool, boolean onlyLevelA) {
        return render("customers", tenantId, maxRows)
                .replace("{poolFilter}", onlyPool ? REPORTS.getProperty("poolFilter") : "")
                .replace("{levelFilter}", onlyLevelA ? REPORTS.getProperty("levelFilter") : "");
    }

    private static Properties load() {
        Properties reports = new Properties();
        try (var stream = Objects.requireNonNull(ReportQueryTemplates.class.getResourceAsStream("/nl2sql/reports.xml"))) {
            reports.loadFromXML(stream);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return reports;
    }
}
