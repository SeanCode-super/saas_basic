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

    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleValidationException(Exception exception) {
        if (exception instanceof MethodArgumentNotValidException methodArgumentNotValidException) {
            String message = methodArgumentNotValidException.getBindingResult()
                    .getFieldErrors()
                    .stream()
                    .map(fieldError -> fieldError.getDefaultMessage() == null ? "参数校验失败" : fieldError.getDefaultMessage())
                    .distinct()
                    .collect(Collectors.joining("；"));
            return ApiResponse.failure("VALIDATION_ERROR", message.isBlank() ? "参数校验失败" : message);
        }
        if (exception instanceof ConstraintViolationException constraintViolationException) {
            String message = constraintViolationException.getConstraintViolations()
                    .stream()
                    .map(violation -> violation.getMessage() == null ? "参数校验失败" : violation.getMessage())
                    .distinct()
                    .collect(Collectors.joining("；"));
            return ApiResponse.failure("VALIDATION_ERROR", message.isBlank() ? "参数校验失败" : message);
        }
        return ApiResponse.failure("VALIDATION_ERROR", "参数校验失败");
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> handleException(Exception exception) {
        return ApiResponse.failure("INTERNAL_SERVER_ERROR", exception.getMessage());
    }

    private HttpStatus resolveStatus(BizException exception) {
        if ("AUTH_FORBIDDEN".equals(exception.getCode())) {
            return HttpStatus.FORBIDDEN;
        }
        if (exception.getCode() != null && exception.getCode().startsWith("AUTH_")) {
            return HttpStatus.UNAUTHORIZED;
        }
        return HttpStatus.BAD_REQUEST;
    }
}
