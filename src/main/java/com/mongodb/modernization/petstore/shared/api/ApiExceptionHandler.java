package com.mongodb.modernization.petstore.shared.api;

import com.mongodb.modernization.petstore.cart.domain.CartLineNotFoundException;
import com.mongodb.modernization.petstore.cart.domain.InvalidQuantityException;
import com.mongodb.modernization.petstore.orders.application.DuplicateCheckoutException;
import com.mongodb.modernization.petstore.orders.application.InsufficientStockException;
import com.mongodb.modernization.petstore.shared.application.NotFoundException;
import com.mongodb.modernization.petstore.shared.application.StoreConflictException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.util.LinkedHashMap;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler({NotFoundException.class, CartLineNotFoundException.class})
    ProblemDetail notFound(RuntimeException error, HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, error.getMessage(), request);
    }

    @ExceptionHandler({InvalidQuantityException.class, ConstraintViolationException.class})
    ProblemDetail badRequest(RuntimeException error, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, error.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail invalidBody(MethodArgumentNotValidException error, HttpServletRequest request) {
        var detail = problem(HttpStatus.BAD_REQUEST, "Request validation failed", request);
        var fields = new LinkedHashMap<String, String>();
        error.getBindingResult().getFieldErrors().forEach(field -> fields.putIfAbsent(field.getField(), field.getDefaultMessage()));
        detail.setProperty("fields", fields);
        return detail;
    }

    @ExceptionHandler({StoreConflictException.class, InsufficientStockException.class, DuplicateCheckoutException.class})
    ProblemDetail conflict(RuntimeException error, HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, error.getMessage(), request);
    }

    @ExceptionHandler(DataAccessException.class)
    ProblemDetail unavailable(DataAccessException error, HttpServletRequest request) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "The data store is temporarily unavailable", request);
    }

    private static ProblemDetail problem(HttpStatus status, String detail, HttpServletRequest request) {
        var problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(status.getReasonPhrase());
        problem.setInstance(URI.create(request.getRequestURI()));
        return problem;
    }
}
