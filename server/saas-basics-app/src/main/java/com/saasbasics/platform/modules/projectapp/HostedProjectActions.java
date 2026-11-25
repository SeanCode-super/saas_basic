package com.saasbasics.platform.modules.projectapp;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saasbasics.platform.common.auth.AuthPrincipal;
import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.modules.codegen.project.ProjectDesign;
import com.saasbasics.platform.modules.tenant.api.GeneratedApplicationExecutor;
import com.saasbasics.platform.modules.tenant.service.IndustryWorkspace;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import static com.saasbasics.platform.modules.projectapp.ProjectSourceClient.at;

@Service
public class HostedProjectActions implements GeneratedApplicationExecutor {
    private final ObjectMapper json;
    private final ProjectConnection connections;
    private final ProjectSourceClient client;
    private final ProjectActionReceipts receipts;
    public HostedProjectActions(ObjectMapper json, ProjectConnection connections, ProjectSourceClient client, ProjectActionReceipts receipts) {
        this.json=json; this.connections=connections; this.client=client; this.receipts=receipts;
    }

    @Override public JsonNode execute(String reviewed,String action,Map<String,String> input,UUID requestId,AuthPrincipal user) {
        ProjectDesign design;
        try { design=json.readValue(reviewed,ProjectDesign.class); }
        catch (java.io.IOException e) { throw new IndustryWorkspace.ServiceUnavailable(); }
        var profile=connections.load(design);
        var member=profile.members().get(user.tenantId()+":"+user.userId());
        if (member==null || !member.actions().contains(action)) throw ProjectConnection.denied();
        String token=client.authenticate(profile,member);
        for (var page:design.pages()) {
            if (action.equals(page.key()+"_list")) {
                var result=json.createArrayNode();
                var display=profile.displayValues()==null ? Map.<String,Map<String,String>>of()
                        : profile.displayValues().getOrDefault(page.key(),Map.of());
                for (JsonNode row : rows(profile,page,token)) {
                    var item=result.addObject();
                    for (var column:page.columns()) {
                        var value=at(row,column.source());
                        var label=display.getOrDefault(column.key(),Map.of()).get(value.asText(""));
                        if (label!=null) item.put(column.key(),label);
                        else if (value.isValueNode()) item.set(column.key(),value);
                        else item.putNull(column.key());
                    }
                }
                return result;
            }
            for (var op:page.actions()) if (op.key().equals(action)) {
                checkTargets(design,profile,member,token,action,input);
                return submit(design,profile,token,op,input,requestId,user);
            }
        }
        throw ProjectConnection.denied();
    }

    private JsonNode rows(ProjectConnection.Profile profile,ProjectDesign.Page page,String token) {
        var data=at(client.send(profile,"GET",page.path(),token,null,null),page.rowsAt());
        if (!data.isArray()) throw new IndustryWorkspace.ServiceUnavailable();
        return data;
    }

    private void checkTargets(ProjectDesign design,ProjectConnection.Profile profile,ProjectConnection.Member member,
                              String token,String action,Map<String,String> input) {
        if (profile.inputScopes()==null) return;
        for (var rule:profile.inputScopes().getOrDefault(action,List.of())) {
            var page=design.pages().stream().filter(p->p.key().equals(rule.page())).findFirst().orElseThrow(ProjectConnection::unconfigured);
            var owned=new HashSet<String>();
            for (var row:rows(profile,page,token)) {
                if (member.tenant().equals(at(row,rule.tenantAt()).asText()) && member.user().equals(at(row,rule.userAt()).asText()))
                    owned.add(at(row,rule.idAt()).asText());
            }
            for (var target:ids(input.get(rule.field()))) if (!owned.contains(target)) throw ProjectConnection.denied();
        }
    }

    private static List<String> ids(String value) {
        if (value==null) throw ProjectSourceClient.rejected();
        var list=Arrays.stream(value.replace('，',',').split(",",-1)).map(String::trim).toList();
        if (list.size()>100 || list.stream().anyMatch(id->!id.matches("[0-9]{1,18}")) || new HashSet<>(list).size()!=list.size())
            throw ProjectSourceClient.rejected();
        return list;
    }

    private JsonNode submit(ProjectDesign design,ProjectConnection.Profile profile,String token,ProjectDesign.Action op,
                            Map<String,String> input,UUID requestId,AuthPrincipal user) {
        var body=json.createObjectNode();
        var query=new StringJoiner("&");
        String path=op.path();
        for (var field:op.fields()) {
            var value=input.get(field.key());
            if (value==null || value.isBlank()) continue;
            if ("path".equals(field.location())) {
                if (!value.matches("[a-zA-Z0-9_-]+")) throw ProjectSourceClient.rejected();
                path=path.replace("{"+field.target()+"}",value);
            } else if ("query".equals(field.location())) query.add(field.target()+"="+URLEncoder.encode(value,StandardCharsets.UTF_8));
            else if ("number_list".equals(field.type())) {
                var array=body.putArray(field.target());
                ids(value).forEach(id->array.add(Long.parseLong(id)));
            } else if ("number".equals(field.type())) body.put(field.target(),new BigDecimal(value));
            else body.put(field.target(),value);
        }
        var receipt=new ProjectActionReceipt();
        receipt.tenantId=user.tenantId(); receipt.userId=user.userId(); receipt.appCode=design.code();
        receipt.requestId=requestId.toString(); receipt.completed=false;
        try {
            byte[] bytes=json.writeValueAsBytes(List.of(design.revision(),op.key(),new TreeMap<>(input)));
            receipt.inputHash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (java.io.IOException | java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
        // Commit the reservation before contacting a source which may not itself implement idempotency.
        try { receipts.insert(receipt); }
        catch (DuplicateKeyException e) {
            var prior=receipts.selectOne(new QueryWrapper<ProjectActionReceipt>().eq("tenant_id",receipt.tenantId)
                    .eq("user_id",receipt.userId).eq("app_code",receipt.appCode).eq("request_id",receipt.requestId));
            if (prior!=null && Boolean.TRUE.equals(prior.completed) && receipt.inputHash.equals(prior.inputHash))
                return json.createObjectNode().put("accepted",true);
            throw new BizException("APP_ACTION_REJECTED","请求已提交，请先核对业务结果");
        }
        var result=client.send(profile,op.method(),path+(query.length()>0 ? "?"+query : ""),token,body.isEmpty()?null:body,requestId);
        if (op.positiveCountAt()!=null && (!at(result,op.positiveCountAt()).isIntegralNumber()
                || at(result,op.positiveCountAt()).asLong()<1)) throw ProjectSourceClient.rejected();
        receipt.completed=true;
        receipt.completedAt=LocalDateTime.now();
        receipts.updateById(receipt);
        return json.createObjectNode().put("accepted",true);
    }
}
