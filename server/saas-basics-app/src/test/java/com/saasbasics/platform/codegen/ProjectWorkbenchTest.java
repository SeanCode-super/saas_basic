package com.saasbasics.platform.codegen;

import com.saasbasics.platform.modules.codegen.project.ProjectDesign;
import com.saasbasics.platform.modules.codegen.project.ProjectWorkbench;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.modules.ai.api.AiGatewayFacade;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ProjectWorkbenchTest {
    private final ObjectMapper json = new ObjectMapper();
    private final Path example = Path.of("../../examples/matrix-push/project-design.json");
    @Test
    void checksModelEndpointEvidenceAndPackagesBothNativeClients() throws Exception {
        var d = json.readValue(example.toFile(),ProjectDesign.class);
        var routes = new LinkedHashMap<String,List<String>>();
        for (var p : d.pages()) {
            var observed = routes.computeIfAbsent(p.evidence(), ignored -> new ArrayList<>());
            observed.add("GET "+p.path());
            for (var a : p.actions()) routes.computeIfAbsent(a.evidence(),ignored -> new ArrayList<>()).add(a.method()+" "+a.path());
        }
        var sources = new ProjectDesign.Sources(routes.entrySet().stream().map(e ->
                new ProjectDesign.Unit(e.getKey(),List.of(),e.getValue())).toList(),"qa");
        String response = json.writeValueAsString(d);
        var w = new ProjectWorkbench(command -> {
            assertThat(command.messages()).extracting(AiGatewayFacade.ChatMessage::role).containsExactly("system","user");
            assertThat(command.messages().getLast().content()).contains("/api/tasks");
            return new AiGatewayFacade.ChatCompletionOutput("test-run","qa",response,1,1,1);
        },json);
        assertThat(w.analyze(sources).design()).isEqualTo(d);
        var review = new ProjectDesign.Review(sources,d);
        var result = w.preview(review);
        assertThat((List<?>) result.manifest().get("pages")).hasSize(5);
        assertThat(result.files()).anyMatch(f -> f.path().endsWith("matrix_push.vue") && f.code().contains("const packagedApplication = \"matrix_push\""));
        assertThat(result.files()).extracting(f -> f.path()).contains("server/console.py","standalone/index.html",
                "standalone/console.css","standalone/console.js","standalone/config.example.json","server/test_console.py");
        assertThat(w.download(review)).isEqualTo(w.download(review));
        routes.get(d.pages().getFirst().evidence()).clear();
        assertThatThrownBy(() -> w.preview(review)).isInstanceOf(BizException.class).hasMessageContaining("源码证据");
        assertThatThrownBy(() -> new ProjectWorkbench(c -> { throw new BizException("AI_PROVIDER_NOT_CONFIGURED","模型未配置"); },json)
                .analyze(sources)).isInstanceOf(BizException.class).hasMessage("模型未配置");
    }
}
