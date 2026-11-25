package com.saasbasics.platform.modules.codegen.project;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.modules.ai.api.AiGatewayFacade;
import com.saasbasics.platform.modules.codegen.dto.CodegenPreviewFileResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

@Service
public class ProjectWorkbench {
    public record Analysis(ProjectDesign design, String model, String runNo) {}
    public record Preview(List<CodegenPreviewFileResponse> files, Map<String,Object> manifest) {}
    private final AiGatewayFacade ai;
    private final ObjectMapper json;
    public ProjectWorkbench(AiGatewayFacade ai, ObjectMapper json) { this.ai = ai; this.json = json; }

    public Analysis analyze(ProjectDesign.Sources sources) throws IOException {
        var catalog = ProjectDesign.inventory(sources);
        String evidence = json.writeValueAsString(sources.units());
        if (sources.previous() != null) {
            String old = json.writeValueAsString(sources.previous());
            ProjectDesign.require(old.length() <= 90000,"原蓝图过大");
            evidence = "已确认的旧蓝图：" + old + "\n新的项目索引：" + evidence;
        }
        var reply = ai.complete(new AiGatewayFacade.ChatCompletionCommand(sources.model(),"project-management-design",
                List.of(new AiGatewayFacade.ChatMessage("system", resource("instructions.txt")),
                        new AiGatewayFacade.ChatMessage("user", evidence)), 0.1));
        try {
            String value = reply.reply() == null ? "" : reply.reply().strip();
            if (value.startsWith("```")) value = value.replaceFirst("^```(?:json)?\\s*","").replaceFirst("\\s*```$","");
            var design = json.readerFor(ProjectDesign.class)
                    .with(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .<ProjectDesign>readValue(value);
            if (design == null) throw new BizException("INVALID_PARAM","没有业务蓝图");
            design.verify(catalog);
            if (sources.previous() != null) {
                ProjectDesign.require(design.code().equals(sources.previous().code())
                        && design.revision() == sources.previous().revision()+1,"增量分析须延续应用编码并递增修订号");
            }
            return new Analysis(design, reply.modelId(), reply.runNo());
        } catch (JsonProcessingException | BizException ex) {
            throw new BizException("AI_DESIGN_INVALID","模型输出无法生成工程：" +
                    (ex instanceof BizException ? ex.getMessage() : "业务蓝图 JSON 格式不完整"));
        }
    }

    public Preview preview(ProjectDesign.Review review) throws IOException {
        Map<String,Object> manifest = reviewedManifest(review);
        var design = review.design();
        var files = new ArrayList<CodegenPreviewFileResponse>();
        add(files,"project-design.json","蓝图",pretty(design));
        add(files,"manifest.json","应用登记",pretty(manifest));
        add(files,"server/service.py","业务接口",resource("service.py"));
        add(files,"server/test_service.py","接口测试",resource("test_service.py"));
        add(files,"server/console.py","独立管理服务",resource("standalone/console.py"));
        add(files,"server/test_console.py","独立服务测试",resource("standalone/test_console.py"));
        for (String asset : List.of("index.html","console.js","console.css","config.example.json")) {
            add(files,"standalone/"+asset,"独立管理端",resource("standalone/"+asset));
        }
        add(files,"web/src/views/industry/"+design.code()+".vue","PC 端",client("web/views/industry/IndustryApplication.vue",design.code()));
        add(files,"mobile/src/pages/"+design.code()+"/index.vue","手机端",client("mobile/pages/industry/app.vue",design.code()));
        add(files,"web/src/api/modules/industry.ts","PC 接口",resource("web/api/modules/industry.ts"));
        add(files,"mobile/src/api/industry.ts","手机接口",resource("mobile/api/industry.ts"));
        add(files,"update_generated.py","增量更新",resource("update_generated.py"));
        add(files,"README.md","部署",resource("README.md"));
        var hashes = new LinkedHashMap<String,String>();
        for (var f : files) hashes.put(f.path(), sha256(f.code()));
        add(files,"source-lock.json","生成基线",pretty(hashes));
        return new Preview(List.copyOf(files), manifest);
    }

    public byte[] download(ProjectDesign.Review review) throws IOException {
        var out = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            for (var f : preview(review).files()) {
                var entry = new ZipEntry(f.path());
                entry.setTime(0);
                zip.putNextEntry(entry);
                zip.write(f.code().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return out.toByteArray();
    }

    public Map<String,Object> reviewedManifest(ProjectDesign.Review review) {
        ProjectDesign.require(review != null && review.design() != null, "请先确认业务蓝图");
        review.design().verify(ProjectDesign.inventory(review.sources()));
        return manifest(review.design());
    }

    private Map<String,Object> manifest(ProjectDesign design) {
        var actions = new ArrayList<Map<String,Object>>();
        var pages = new ArrayList<Map<String,Object>>();
        actions.add(Map.of("key","query","label","进入应用","fields",List.of()));
        for (var p : design.pages()) {
            String query = p.key()+"_list";
            actions.add(Map.of("key",query,"label","查看"+p.label(),"fields",List.of()));
            for (var a : p.actions()) {
                var fields = a.fields().stream().map(f -> Map.of("key",f.key(),"label","number_list".equals(f.type()) ? f.label()+"（逗号分隔）" : f.label(),"type","number_list".equals(f.type()) ? "text" : f.type(),"required",f.required())).toList();
                actions.add(Map.of("key",a.key(),"label",a.label(),"fields",fields));
            }
            var columns = p.columns().stream().map(c -> Map.of("key",c.key(),"label",c.label(),"type",c.type(),"required",false)).toList();
            pages.add(Map.of("key",p.key(),"label",p.label(),"query",query,"columns",columns,
                    "actions",p.actions().stream().map(ProjectDesign.Action::key).toList()));
        }
        return Map.of("code",design.code(),"name",design.name(),"description",design.description(),"revision",design.revision(),
                "kind","NATIVE","pc",true,"mobile",true,"columns",List.of(),"actions",actions,"pages",pages);
    }

    private String pretty(Object value) throws JsonProcessingException {
        return json.writerWithDefaultPrettyPrinter().with(com.fasterxml.jackson.databind.SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .writeValueAsString(value)+"\n";
    }
    private static String client(String file, String code) throws IOException {
        String template = resource(file);
        String marker = "const packagedApplication = \"\";";
        if (!template.contains(marker)) throw new IOException("缺少应用客户端入口");
        return template.replace(marker, "const packagedApplication = \"" + code + "\";");
    }
    private static String resource(String name) throws IOException {
        return new ClassPathResource("codegen/project/"+name).getContentAsString(StandardCharsets.UTF_8);
    }
    private static void add(List<CodegenPreviewFileResponse> files, String path, String layer, String code) {
        String name = path.substring(path.lastIndexOf('/')+1);
        files.add(new CodegenPreviewFileResponse(path,name,layer,name.substring(name.lastIndexOf('.')+1),code));
    }
    private static String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
