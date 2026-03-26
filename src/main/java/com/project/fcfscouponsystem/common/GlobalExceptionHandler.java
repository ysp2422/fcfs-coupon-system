package com.project.fcfscouponsystem.common;

import com.project.fcfscouponsystem.domain.exception.DuplicateIssueException;
import com.project.fcfscouponsystem.domain.exception.OutOfStockException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DuplicateIssueException.class)
    public ResponseEntity<ApiResponse<?>> handleDuplicateIssue(DuplicateIssueException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.fail(e.getMessage()));
    }

    @ExceptionHandler(OutOfStockException.class)
    public ResponseEntity<ApiResponse<?>> handleOutOfStock(OutOfStockException e) {
        return ResponseEntity.status(HttpStatus.GONE)
                .body(ApiResponse.fail(e.getMessage()));
    }
}
