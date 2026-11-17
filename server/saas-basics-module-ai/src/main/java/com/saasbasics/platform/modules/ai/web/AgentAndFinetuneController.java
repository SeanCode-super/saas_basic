package com.saasbasics.platform.modules.ai.web;

import com.saasbasics.platform.common.api.ApiResponse;
import com.saasbasics.platform.common.auth.RequirePermission;
import com.saasbasics.platform.modules.ai.application.dto.AiPlatformDtos.AgentChatRequest;
import com.saasbasics.platform.modules.ai.application.dto.AiPlatformDtos.AgentChatResponse;
import com.saasbasics.platform.modules.ai.application.dto.AiPlatformDtos.AiAgentDTO;
import com.saasbasics.platform.modules.ai.application.dto.AiPlatformDtos.AiAgentSaveRequest;
import com.saasbasics.platform.modules.ai.application.dto.AiPlatformDtos.AiToolOptionDTO;
import com.saasbasics.platform.modules.ai.application.dto.AiPlatformDtos.FinetuneDatasetDTO;
import com.saasbasics.platform.modules.ai.application.dto.AiPlatformDtos.FinetuneDatasetSaveRequest;
import com.saasbasics.platform.modules.ai.application.dto.AiPlatformDtos.FinetuneJobCreateRequest;
import com.saasbasics.platform.modules.ai.application.dto.AiPlatformDtos.FinetuneJobDTO;
import com.saasbasics.platform.modules.ai.application.dto.AiPlatformDtos.FinetuneSampleAddRequest;
import com.saasbasics.platform.modules.ai.application.dto.AiPlatformDtos.FinetuneSampleDTO;
import com.saasbasics.platform.modules.ai.application.dto.AiPlatformDtos.FinetuneSampleReviewRequest;
import com.saasbasics.platform.modules.ai.application.service.AgentStudioService;
import com.saasbasics.platform.modules.ai.application.service.FinetuneStudioService;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai")
@RequirePermission("system:config:query")
public class AgentAndFinetuneController {

    private final AgentStudioService agentStudioService;
    private final FinetuneStudioService finetuneStudioService;

    public AgentAndFinetuneController(
            AgentStudioService agentStudioService,
            FinetuneStudioService finetuneStudioService
    ) {
        this.agentStudioService = agentStudioService;
        this.finetuneStudioService = finetuneStudioService;
    }

    // --- Visual Agent Studio ---
    @GetMapping("/agents")
    public ApiResponse<List<AiAgentDTO>> listAgents() {
        return ApiResponse.success(agentStudioService.listAgents());
    }

    @GetMapping("/agents/tools")
    public ApiResponse<List<AiToolOptionDTO>> listTools() {
        return ApiResponse.success(agentStudioService.listToolCatalog());
    }

    @PostMapping("/agents")
    @RequirePermission("system:config:write")
    public ApiResponse<AiAgentDTO> saveAgent(@RequestBody AiAgentSaveRequest request) {
        return ApiResponse.success(agentStudioService.saveAgent(request));
    }

    @DeleteMapping("/agents/{id}")
    @RequirePermission("system:config:write")
    public ApiResponse<Void> deleteAgent(@PathVariable("id") Long id) {
        agentStudioService.deleteAgent(id);
        return ApiResponse.success(null);
    }

    @PostMapping("/agents/chat")
    public ApiResponse<AgentChatResponse> chat(@RequestBody AgentChatRequest request) {
        return ApiResponse.success(agentStudioService.chatWithAgent(request));
    }

    @GetMapping("/copilot/crm-suggestion")
    public ApiResponse<Map<String, Object>> crmSuggestion(
            @RequestParam(name = "bizType", defaultValue = "CUSTOMER") String bizType,
            @RequestParam(name = "bizId") Long bizId) {
        return ApiResponse.success(agentStudioService.buildCrmFollowUpSuggestion(bizType, bizId));
    }

    // --- Fine-tuning Workbench ---
    @GetMapping("/finetune/datasets")
    public ApiResponse<List<FinetuneDatasetDTO>> listDatasets() {
        return ApiResponse.success(finetuneStudioService.listDatasets());
    }

    @PostMapping("/finetune/datasets")
    @RequirePermission("system:config:write")
    public ApiResponse<FinetuneDatasetDTO> saveDataset(@RequestBody FinetuneDatasetSaveRequest request) {
        return ApiResponse.success(finetuneStudioService.saveDataset(request));
    }

    @GetMapping("/finetune/samples")
    public ApiResponse<List<FinetuneSampleDTO>> listSamples(
            @RequestParam(name = "datasetId", required = false) Long datasetId) {
        return ApiResponse.success(finetuneStudioService.listSamples(datasetId));
    }

    @PostMapping("/finetune/samples")
    @RequirePermission("system:config:write")
    public ApiResponse<FinetuneSampleDTO> addSample(@RequestBody FinetuneSampleAddRequest request) {
        return ApiResponse.success(finetuneStudioService.addSample(request));
    }

    @PostMapping("/finetune/datasets/{datasetId}/harvest-nl2sql")
    @RequirePermission("system:config:write")
    public ApiResponse<Map<String, Object>> harvestNl2Sql(
            @PathVariable("datasetId") Long datasetId,
            @RequestParam(name = "limit", defaultValue = "15") int limit) {
        return ApiResponse.success(finetuneStudioService.harvestFromNl2SqlHistory(datasetId, limit));
    }

    @PostMapping("/finetune/samples/{id}/review")
    @RequirePermission("system:config:write")
    public ApiResponse<FinetuneSampleDTO> reviewSample(
            @PathVariable("id") Long id,
            @RequestBody FinetuneSampleReviewRequest request) {
        return ApiResponse.success(finetuneStudioService.reviewSample(id, request.reviewStatus()));
    }

    @GetMapping("/finetune/datasets/{datasetId}/export-jsonl")
    public ResponseEntity<byte[]> exportJsonl(
            @PathVariable("datasetId") Long datasetId,
            @RequestParam(name = "formatType", defaultValue = "ALPACA") String formatType) {
        byte[] bytes = finetuneStudioService.exportJsonl(datasetId, formatType);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"finetune_dataset_" + datasetId + ".jsonl\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(bytes.length)
                .body(bytes);
    }

    @GetMapping("/finetune/jobs")
    public ApiResponse<List<FinetuneJobDTO>> listJobs() {
        return ApiResponse.success(finetuneStudioService.listJobs());
    }

    @PostMapping("/finetune/jobs")
    @RequirePermission("system:config:write")
    public ApiResponse<FinetuneJobDTO> startJob(@RequestBody FinetuneJobCreateRequest request) {
        return ApiResponse.success(finetuneStudioService.startTrainingJob(request));
    }
}
