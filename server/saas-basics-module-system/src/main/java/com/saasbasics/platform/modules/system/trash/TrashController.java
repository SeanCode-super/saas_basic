package com.saasbasics.platform.modules.system.trash;

import com.saasbasics.platform.common.api.ApiResponse;
import com.saasbasics.platform.repeat.RepeatSubmit;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/system/trash")
public class TrashController {

    private final TrashService trashService;

    public TrashController(TrashService trashService) {
        this.trashService = trashService;
    }

    @GetMapping
    public ApiResponse<List<TrashItemResponse>> list(
            @RequestParam(required = false) String appCode,
            @RequestParam(required = false) String tableName,
            @RequestParam(required = false) String keyword
    ) {
        return ApiResponse.success(trashService.listTrash(appCode, tableName, keyword));
    }

    @GetMapping("/apps")
    public ApiResponse<List<TrashAppSummaryResponse>> apps() {
        return ApiResponse.success(trashService.getAppSummaries());
    }

    @PostMapping("/restore")
    @RepeatSubmit(interval = 1500, message = "数据恢复中，请勿重复操作")
    public ApiResponse<TrashRestoreResponse> restore(@Valid @RequestBody TrashActionRequest req) {
        TrashRestoreResponse response = trashService.restore(req);
        return ApiResponse.success(response);
    }

    @PostMapping("/purge")
    @RepeatSubmit(interval = 1500, message = "数据清理中，请勿重复操作")
    public ApiResponse<Void> purge(@Valid @RequestBody TrashActionRequest req) {
        trashService.purge(req);
        return ApiResponse.success(null);
    }
}
