package com.saasbasics.platform.extension;

import static org.assertj.core.api.Assertions.assertThat;

import com.saasbasics.platform.modules.codegen.dto.CodegenTableDefinitionRequest;
import com.saasbasics.platform.modules.codegen.dto.CodegenTableDefinitionRequest.CodegenColumnDefinition;
import com.saasbasics.platform.modules.codegen.engine.CodegenEngineService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class GeneratedApplicationTest {
    @TempDir Path directory;

    @ParameterizedTest
    @ValueSource(strings = {"SINGLE", "EXTENSION"})
    void exportedJavaCompilesAgainstTheHost(String template) throws Exception {
        var columns = List.of(
                new CodegenColumnDefinition("id", "id", "bigint", "Long", "ID", true, true, false, null, "number", null),
                new CodegenColumnDefinition("tenant_id", "tenantId", "bigint", "Long", "租户", false, true, false, null, "number", null),
                new CodegenColumnDefinition("teacher_email", "teacherEmail", "varchar(128)", "String", "邮箱", false, true, false, null, "input", null));
        var model = new CodegenTableDefinitionRequest("biz_course", "课程", "Course", "education", "课程", "course",
                "org.example", columns, template, null, null, null, null, null, null, null, null);
        var arguments = new ArrayList<>(List.of("-proc:none", "-classpath", System.getProperty("java.class.path"),
                "-d", directory.toString()));
        for (var file : new CodegenEngineService().generateFiles(model)) {
            var path = directory.resolve(file.path());
            Files.createDirectories(path.getParent());
            Files.writeString(path, file.code());
            if (file.name().endsWith(".java")) arguments.add(path.toString());
            if (file.name().equals("CourseSaveRequest.java")) assertThat(file.code()).doesNotContain("tenantId");
        }
        assertThat(ToolProvider.getSystemJavaCompiler().run(null, null, null, arguments.toArray(String[]::new))).isZero();
        if (template.equals("EXTENSION")) {
            try (var loader = new java.net.URLClassLoader(new java.net.URL[]{directory.toUri().toURL()}, getClass().getClassLoader())) {
                var app = (com.saasbasics.platform.common.tenant.TenantApplication) loader.loadClass("org.example.education.CourseApplication")
                        .getDeclaredConstructor().newInstance();
                assertThat(app.componentPath()).isEqualTo("@/views/industry/IndustryApplication.vue");
                assertThat(app.workspaceFields(1)).containsExactly(new com.saasbasics.platform.common.tenant.TenantApplication.WorkspaceField("teacherEmail", "邮箱", "text", true));
            }
            assertThat(directory.resolve("server/pom.xml")).isRegularFile();
            assertThat(directory.resolve("server/src/main/resources/extensions/education/db/V1__init.sql")).isRegularFile();
            assertThat(directory.resolve("server/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports"))
                    .hasContent("org.example.education.CourseApplication\n");
        }
    }
}
