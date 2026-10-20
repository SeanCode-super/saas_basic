package com.saasbasics.platform.common.exception;

import com.saasbasics.platform.common.api.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ConstraintViolationException;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public ApiResponse<Void> handleBizException(BizException exception, HttpServletResponse response) {
        response.setStatus(resolveStatus(exception).value());
        return ApiResponse.failure(exception.getCode(), exception.getMessage());
    }

    private static final java.util.Map<String, String> FIELD_NAME_MAP = java.util.Map.ofEntries(
            java.util.Map.entry("menuName", "资源名称"),
            java.util.Map.entry("menuCode", "资源标识编码"),
            java.util.Map.entry("menuType", "资源类型"),
            java.util.Map.entry("tenantName", "租户名称"),
            java.util.Map.entry("tenantCode", "租户编码"),
            java.util.Map.entry("deptName", "部门名称"),
            java.util.Map.entry("deptCode", "部门编码"),
            java.util.Map.entry("positionName", "岗位名称"),
            java.util.Map.entry("positionCode", "岗位编码"),
            java.util.Map.entry("employeeNo", "工号"),
            java.util.Map.entry("employeeName", "员工姓名"),
            java.util.Map.entry("username", "登录账号"),
            java.util.Map.entry("password", "登录密码"),
            java.util.Map.entry("roleName", "角色名称"),
            java.util.Map.entry("roleCode", "角色编码"),
            java.util.Map.entry("resourceName", "资源名称"),
            java.util.Map.entry("resourceCode", "资源编码"),
            java.util.Map.entry("urlPattern", "请求路径规则"),
            java.util.Map.entry("dictName", "字典名称"),
            java.util.Map.entry("dictCode", "字典类型编码"),
            java.util.Map.entry("itemLabel", "字典项名称"),
            java.util.Map.entry("itemValue", "字典项键值"),
            java.util.Map.entry("name", "名称"),
            java.util.Map.entry("code", "编码"),
            java.util.Map.entry("key", "配置键名"),
            java.util.Map.entry("value", "配置键值"),
            java.util.Map.entry("group", "配置分组"),
            java.util.Map.entry("handlerName", "执行器处理器"),
            java.util.Map.entry("alarmName", "告警策略名称"),
            java.util.Map.entry("alarmCode", "告警策略编码"),
            java.util.Map.entry("storageName", "存储配置名称"),
            java.util.Map.entry("storageCode", "存储标识编码"),
            java.util.Map.entry("status", "状态"),
            java.util.Map.entry("isolationMode", "隔离模式"),
            java.util.Map.entry("scopeName", "数据范围名称"),
            java.util.Map.entry("scopeCode", "数据范围编码"),
            java.util.Map.entry("policyName", "策略名称"),
            java.util.Map.entry("policyCode", "策略编码")
    );

    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleValidationException(Exception exception) {
        if (exception instanceof MethodArgumentNotValidException methodArgumentNotValidException) {
            String message = methodArgumentNotValidException.getBindingResult()
                    .getFieldErrors()
                    .stream()
                    .map(this::formatFieldError)
                    .distinct()
                    .collect(Collectors.joining("；"));
            return ApiResponse.failure("VALIDATION_ERROR", message.isBlank() ? "参数校验失败" : message);
        }
        if (exception instanceof ConstraintViolationException constraintViolationException) {
            String message = constraintViolationException.getConstraintViolations()
                    .stream()
                    .map(this::formatConstraintViolation)
                    .distinct()
                    .collect(Collectors.joining("；"));
            return ApiResponse.failure("VALIDATION_ERROR", message.isBlank() ? "参数校验失败" : message);
        }
        return ApiResponse.failure("VALIDATION_ERROR", "参数校验失败");
    }

    private String formatFieldError(org.springframework.validation.FieldError fieldError) {
        String field = fieldError.getField();
        String defaultMsg = fieldError.getDefaultMessage();
        if (defaultMsg == null || defaultMsg.isBlank()) {
            defaultMsg = "不得为空白";
        }
        String label = FIELD_NAME_MAP.getOrDefault(field, field);
        if (defaultMsg.contains(label) || defaultMsg.contains(field)) {
            return defaultMsg;
        }
        return label + " " + defaultMsg;
    }

    private String formatConstraintViolation(jakarta.validation.ConstraintViolation<?> violation) {
        String path = violation.getPropertyPath().toString();
        int lastDot = path.lastIndexOf('.');
        String field = lastDot >= 0 ? path.substring(lastDot + 1) : path;
        String defaultMsg = violation.getMessage();
        if (defaultMsg == null || defaultMsg.isBlank()) {
            defaultMsg = "不得为空白";
        }
        String label = FIELD_NAME_MAP.getOrDefault(field, field);
        if (defaultMsg.contains(label) || defaultMsg.contains(field)) {
            return defaultMsg;
        }
        return label + " " + defaultMsg;
    }

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> handleException(Exception exception) {
        log.error("Unhandled server exception: ", exception);
        String msg = exception.getMessage();
        if (msg == null || msg.isBlank()) {
            msg = exception.getClass().getSimpleName();
        }
        return ApiResponse.failure("INTERNAL_SERVER_ERROR", msg);
    }

    private HttpStatus resolveStatus(BizException exception) {
        if ("AUTH_FORBIDDEN".equals(exception.getCode())) {
            return HttpStatus.FORBIDDEN;
        }
        if (exception.getCode() != null && exception.getCode().startsWith("AUTH_")) {
            return HttpStatus.UNAUTHORIZED;
        }
        if (exception.getCode() != null && exception.getCode().endsWith("_NOT_FOUND")) {
            return HttpStatus.NOT_FOUND;
        }
        if (isConflict(exception.getCode())) {
            return HttpStatus.CONFLICT;
        }
        return HttpStatus.BAD_REQUEST;
    }

    private boolean isConflict(String code) {
        if (code == null) {
            return false;
        }
        return code.endsWith("_DUPLICATE")
                || code.endsWith("_CONFLICT")
                || code.endsWith("_REFERENCED")
                || code.endsWith("_REFERENCES_EXIST")
                || code.endsWith("_ACTIVE_DEPENDENTS")
                || code.endsWith("_ACTIVE_ASSIGNMENTS")
                || code.endsWith("_ACTIVE_ENGAGEMENTS")
                || code.endsWith("_DEPENDENTS")
                || code.contains("_UNARCHIVED_")
                || code.endsWith("_TRANSITION_INVALID")
                || code.endsWith("_IMMUTABLE")
                || code.endsWith("_REQUIRES_ARCHIVED")
                || code.endsWith("_TERMINATION_REQUIRED");
    }
}
