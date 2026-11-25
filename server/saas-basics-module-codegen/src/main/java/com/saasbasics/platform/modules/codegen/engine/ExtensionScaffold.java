package com.saasbasics.platform.modules.codegen.engine;

import com.saasbasics.platform.modules.codegen.dto.CodegenPreviewFileResponse;
import com.saasbasics.platform.modules.codegen.dto.CodegenTableDefinitionRequest.CodegenColumnDefinition;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.core.io.ClassPathResource;

final class ExtensionScaffold {
    private static final Set<String> MANAGED = Set.of("id", "tenant_id", "created_by", "created_at",
            "updated_by", "updated_at", "deleted", "deleted_at", "version", "remark");

    private ExtensionScaffold() {}

    static List<CodegenColumnDefinition> tenantColumns(List<CodegenColumnDefinition> source) {
        var fields = new ArrayList<CodegenColumnDefinition>();
        fields.add(new CodegenColumnDefinition("id", "id", "bigint", "Long", "ID", true, true, false,
                null, "number", null));
        fields.add(new CodegenColumnDefinition("tenant_id", "tenantId", "bigint", "Long", "租户", false, true,
                false, null, "number", null));
        for (var field : source) {
            if (MANAGED.contains(field.columnName())) continue;
            if (Boolean.TRUE.equals(field.isPk())) {
                throw new IllegalArgumentException("行业包模板使用 id 作为主键");
            }
            fields.add(field);
        }
        return fields;
    }

    static List<CodegenPreviewFileResponse> files(String pkg, String code, String type, String label, String fields) {
        String entry = pkg + "." + code + "." + type + "Application";
        return List.of(
                new CodegenPreviewFileResponse("server/pom.xml", "pom.xml", "Maven", "xml",
                        template("extension-pom.xml.tpl", pkg, code, type, label)),
                new CodegenPreviewFileResponse("server/src/main/java/" + entry.replace('.', '/') + ".java",
                        type + "Application.java", "Extension", "java",
                        template("extension.java.tpl", pkg, code, type, label).replace("@FIELDS@", fields)),
                new CodegenPreviewFileResponse("server/src/main/resources/META-INF/spring/"
                        + "org.springframework.boot.autoconfigure.AutoConfiguration.imports",
                        "AutoConfiguration.imports", "Spring Boot", "text", entry + "\n"));
    }

    static String literal(String value) {
        return "\"" + new String(com.fasterxml.jackson.core.io.JsonStringEncoder.getInstance().quoteAsString(value)) + "\"";
    }

    private static String template(String filename, String pkg, String code, String type, String label) {
        try {
            return new ClassPathResource("codegen/" + filename).getContentAsString(StandardCharsets.UTF_8)
                    .replace("@PACKAGE@", pkg).replace("@CODE@", code).replace("@TYPE@", type)
                    .replace("@LABEL@", new String(com.fasterxml.jackson.core.io.JsonStringEncoder.getInstance().quoteAsString(label)));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
