package com.saasbasics.platform.common.exception;

import com.saasbasics.platform.common.api.ApiResponse;
import com.saasbasics.platform.common.api.ProblemDetails;
import com.saasbasics.platform.common.trace.TraceContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final Map<String, String> FIELD_NAME_MAP = Map.ofEntries(
            Map.entry("menuName", "资源名称"),
            Map.entry("menuCode", "资源标识编码"),
            Map.entry("menuType", "资源类型"),
            Map.entry("tenantName", "租户名称"),
            Map.entry("tenantCode", "租户编码"),
            Map.entry("deptName", "部门名称"),
            Map.entry("deptCode", "部门编码"),
            Map.entry("positionName", "岗位名称"),
            Map.entry("positionCode", "岗位编码"),
            Map.entry("employeeNo", "工号"),
            Map.entry("employeeName", "员工姓名"),
            Map.entry("username", "登录账号"),
            Map.entry("password", "登录密码"),
            Map.entry("roleName", "角色名称"),
            Map.entry("roleCode", "角色编码"),
            Map.entry("resourceName", "资源名称"),
            Map.entry("resourceCode", "资源编码"),
            Map.entry("urlPattern", "请求路径规则"),
            Map.entry("dictName", "字典名称"),
            Map.entry("dictCode", "字典类型编码"),
            Map.entry("itemLabel", "字典项名称"),
            Map.entry("itemValue", "字典项键值"),
            Map.entry("name", "名称"),
            Map.entry("code", "编码"),
            Map.entry("key", "配置键名"),
            Map.entry("value", "配置键值"),
            Map.entry("group", "配置分组"),
            Map.entry("handlerName", "执行器处理器"),
            Map.entry("alarmName", "告警策略名称"),
            Map.entry("alarmCode", "告警策略编码"),
            Map.entry("storageName", "存储配置名称"),
            Map.entry("storageCode", "存储标识编码"),
            Map.entry("status", "状态"),
            Map.entry("isolationMode", "隔离模式"),
            Map.entry("scopeName", "数据范围名称"),
            Map.entry("scopeCode", "数据范围编码"),
            Map.entry("policyName", "策略名称"),
            Map.entry("policyCode", "策略编码")
    );

    @ExceptionHandler(BizException.class)
    public ResponseEntity<Object> handleBizException(BizException exception, HttpServletRequest request) {
        HttpStatus status = resolveStatus(exception);
        return buildErrorResponse(status, exception.getCode(), exception.getMessage(), request);
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class})
    public ResponseEntity<Object> handleValidationException(Exception exception, HttpServletRequest request) {
        boolean english = isEnglishLocale(request);
        String defaultMsg = english ? "Validation failed" : "参数校验失败";
        String message = defaultMsg;
        if (exception instanceof MethodArgumentNotValidException methodArgumentNotValidException) {
            String joined = methodArgumentNotValidException.getBindingResult()
                    .getFieldErrors()
                    .stream()
                    .map(err -> formatFieldError(err, english))
                    .distinct()
                    .collect(Collectors.joining(english ? "; " : "；"));
            if (!joined.isBlank()) {
                message = joined;
            }
        } else if (exception instanceof ConstraintViolationException constraintViolationException) {
            String joined = constraintViolationException.getConstraintViolations()
                    .stream()
                    .map(v -> formatConstraintViolation(v, english))
                    .distinct()
                    .collect(Collectors.joining(english ? "; " : "；"));
            if (!joined.isBlank()) {
                message = joined;
            }
        }
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message, request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Object> handleNoResourceFound(NoResourceFoundException exception, HttpServletRequest request) {
        boolean english = isEnglishLocale(request);
        String msg = (english ? "Resource not found: " : "请求的资源未找到: ") + exception.getResourcePath();
        return buildErrorResponse(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", msg, request);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Object> handleMissingParam(MissingServletRequestParameterException exception, HttpServletRequest request) {
        boolean english = isEnglishLocale(request);
        String msg = (english ? "Missing required request parameter: " : "缺少必填请求参数: ") + exception.getParameterName();
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "PARAM_MISSING", msg, request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleException(Exception exception, HttpServletRequest request) {
        log.error("Unhandled server exception: ", exception);
        String msg = exception.getMessage();
        if (msg == null || msg.isBlank()) {
            msg = exception.getClass().getSimpleName();
        }
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR", msg, request);
    }

    private ResponseEntity<Object> buildErrorResponse(HttpStatus status, String code, String message, HttpServletRequest request) {
        if (wantsProblemDetails(request)) {
            String instancePath = request != null ? request.getRequestURI() : "/";
            ProblemDetails problem = ProblemDetails.of(
                    status.value(),
                    status.getReasonPhrase(),
                    message,
                    code,
                    instancePath,
                    TraceContext.getTraceId()
            );
            return ResponseEntity.status(status)
                    .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                    .body(problem);
        }
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(ApiResponse.failure(code, message));
    }

    private boolean wantsProblemDetails(HttpServletRequest request) {
        if (request == null) {
            return false;
        }
        String accept = request.getHeader("Accept");
        if (accept != null && accept.toLowerCase(Locale.ROOT).contains("application/problem+json")) {
            return true;
        }
        String customHeader = request.getHeader("X-Error-Format");
        if ("problem+json".equalsIgnoreCase(customHeader)) {
            return true;
        }
        String uri = request.getRequestURI();
        return uri != null && uri.startsWith("/scim/v2/");
    }

    private boolean isEnglishLocale(HttpServletRequest request) {
        if (request == null) {
            return false;
        }
        String lang = request.getHeader("Accept-Language");
        return lang != null && lang.trim().toLowerCase(Locale.ROOT).startsWith("en");
    }

    private String formatFieldError(org.springframework.validation.FieldError fieldError, boolean english) {
        String field = fieldError.getField();
        String defaultMsg = fieldError.getDefaultMessage();
        if (defaultMsg == null || defaultMsg.isBlank()) {
            defaultMsg = english ? "must not be blank" : "不得为空白";
        }
        String label = english ? field : FIELD_NAME_MAP.getOrDefault(field, field);
        if (defaultMsg.contains(label) || defaultMsg.contains(field)) {
            return defaultMsg;
        }
        return label + " " + defaultMsg;
    }

    private String formatConstraintViolation(jakarta.validation.ConstraintViolation<?> violation, boolean english) {
        String path = violation.getPropertyPath().toString();
        int lastDot = path.lastIndexOf('.');
        String field = lastDot >= 0 ? path.substring(lastDot + 1) : path;
        String defaultMsg = violation.getMessage();
        if (defaultMsg == null || defaultMsg.isBlank()) {
            defaultMsg = english ? "must not be blank" : "不得为空白";
        }
        String label = english ? field : FIELD_NAME_MAP.getOrDefault(field, field);
        if (defaultMsg.contains(label) || defaultMsg.contains(field)) {
            return defaultMsg;
        }
        return label + " " + defaultMsg;
    }

    private HttpStatus resolveStatus(BizException exception) {
        if ("RATE_LIMIT_EXCEEDED".equals(exception.getCode())
                || "TOO_MANY_REQUESTS".equals(exception.getCode())
                || (exception.getCode() != null && exception.getCode().endsWith("_QUOTA_EXCEEDED"))) {
            return HttpStatus.TOO_MANY_REQUESTS;
        }
        if ("AUTH_FORBIDDEN".equals(exception.getCode()) || "TENANT_ACCESS_DENIED".equals(exception.getCode())) {
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
