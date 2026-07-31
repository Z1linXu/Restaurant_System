package com.restaurant.system.common.exception;

import com.restaurant.system.common.auth.ForbiddenException;
import com.restaurant.system.common.auth.UnauthorizedException;
import com.restaurant.system.common.feature.FeatureDisabledException;
import com.restaurant.system.common.response.ApiResponse;
import com.restaurant.system.order.exception.OrderSubmissionException;
import com.restaurant.system.owner.exception.OwnerStoreMenuCloneException;
import com.restaurant.system.owner.exception.OwnerStoreOnboardingException;
import jakarta.validation.ConstraintViolationException;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException ex) {
        return ResponseEntity.badRequest().body(ApiResponse.failure(ex.getMessage()));
    }

    @ExceptionHandler(OrderSubmissionException.class)
    public ResponseEntity<ApiResponse<Void>> handleOrderSubmissionException(OrderSubmissionException ex) {
        return ResponseEntity.status(ex.getStatus()).body(ApiResponse.failure(ex.getErrorCode(), ex.getMessage()));
    }

    @ExceptionHandler(OwnerStoreOnboardingException.class)
    public ResponseEntity<ApiResponse<Void>> handleOwnerStoreOnboardingException(OwnerStoreOnboardingException ex) {
        return ResponseEntity.status(ex.getStatus()).body(ApiResponse.failure(ex.getErrorCode(), ex.getMessage()));
    }

    @ExceptionHandler(OwnerStoreMenuCloneException.class)
    public ResponseEntity<ApiResponse<Void>> handleOwnerStoreMenuCloneException(OwnerStoreMenuCloneException ex) {
        return ResponseEntity.status(ex.getStatus()).body(ApiResponse.failure(ex.getErrorCode(), ex.getMessage()));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingRequestHeader(MissingRequestHeaderException ex) {
        return ResponseEntity.badRequest().body(ApiResponse.failure(
            "REQUEST_HEADER_REQUIRED",
            "Required request header: " + ex.getHeaderName()
        ));
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnauthorizedException(UnauthorizedException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.failure(ex.getMessage()));
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ApiResponse<Void>> handleForbiddenException(ForbiddenException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.failure(ex.getMessage()));
    }

    @ExceptionHandler(FeatureDisabledException.class)
    public ResponseEntity<ApiResponse<Void>> handleFeatureDisabledException(FeatureDisabledException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.failure(ex.getMessage()));
    }

    @ExceptionHandler({
        MethodArgumentNotValidException.class,
        BindException.class,
        ConstraintViolationException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleValidationException(Exception ex) {
        String message = "Validation failed";
        if (ex instanceof MethodArgumentNotValidException methodArgumentNotValidException) {
            message = methodArgumentNotValidException.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .collect(Collectors.joining("; "));
        } else if (ex instanceof BindException bindException) {
            message = bindException.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .collect(Collectors.joining("; "));
        } else if (ex instanceof ConstraintViolationException constraintViolationException) {
            message = constraintViolationException.getConstraintViolations().stream()
                .map(constraintViolation -> constraintViolation.getPropertyPath() + ": " + constraintViolation.getMessage())
                .collect(Collectors.joining("; "));
        }

        return ResponseEntity.badRequest().body(ApiResponse.failure(message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiResponse.failure("Internal server error"));
    }
}
