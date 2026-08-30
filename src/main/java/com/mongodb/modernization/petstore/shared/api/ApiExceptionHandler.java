package com.mongodb.modernization.petstore.shared.api;

import com.mongodb.modernization.petstore.analytics.application.InvalidAnalyticsRangeException;
import com.mongodb.modernization.petstore.cart.domain.CartLineNotFoundException;
import com.mongodb.modernization.petstore.accounts.application.AccountAlreadyExistsException;
import com.mongodb.modernization.petstore.cart.domain.InvalidQuantityException;
import com.mongodb.modernization.petstore.orders.application.DuplicateCheckoutException;
import com.mongodb.modernization.petstore.orders.application.InsufficientStockException;
import com.mongodb.modernization.petstore.payments.application.PaymentDeclinedException;
import com.mongodb.modernization.petstore.shared.application.NotFoundException;
import com.mongodb.modernization.petstore.shared.application.StoreConflictException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import java.net.URI;
import java.util.LinkedHashMap;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler({NotFoundException.class, CartLineNotFoundException.class})
    ProblemDetail notFound(RuntimeException error, HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, error.getMessage(), request);
    }

    @ExceptionHandler({InvalidQuantityException.class, ConstraintViolationException.class,
            InvalidAnalyticsRangeException.class})
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

    @ExceptionHandler(HandlerMethodValidationException.class)
    ProblemDetail invalidMethodArguments(HandlerMethodValidationException error, HttpServletRequest request) {
        var detail = problem(HttpStatus.BAD_REQUEST, "Request validation failed", request);
        var fields = new LinkedHashMap<String, String>();
        for (var result : error.getParameterValidationResults()) {
            if (result instanceof ParameterErrors parameterErrors) {
                parameterErrors.getFieldErrors().forEach(field ->
                        fields.putIfAbsent(field.getField(), field.getDefaultMessage()));
            } else {
                var parameter = result.getMethodParameter().getParameterName();
                result.getResolvableErrors().forEach(validation ->
                        fields.putIfAbsent(parameter == null ? "request" : parameter, validation.getDefaultMessage()));
            }
        }
        detail.setProperty("fields", fields);
        return detail;
    }

    @ExceptionHandler({StoreConflictException.class, InsufficientStockException.class, DuplicateCheckoutException.class})
    ProblemDetail conflict(RuntimeException error, HttpServletRequest request) {
        LOG.atWarn()
                .addKeyValue("event", "api.conflict")
                .addKeyValue("path", request.getRequestURI())
                .addKeyValue("errorType", error.getClass().getSimpleName())
                .log("Request rejected by a concurrency or inventory guard");
        return problem(HttpStatus.CONFLICT, error.getMessage(), request);
    }

    @ExceptionHandler(AccountAlreadyExistsException.class)
    ProblemDetail duplicateAccount(AccountAlreadyExistsException error, HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, error.getMessage(), request);
    }

    @ExceptionHandler(PaymentDeclinedException.class)
    ProblemDetail paymentDeclined(PaymentDeclinedException error, HttpServletRequest request) {
        LOG.atWarn().addKeyValue("event", "payment.authorization.declined")
                .addKeyValue("path", request.getRequestURI()).log("Payment authorization declined");
        return problem(HttpStatus.UNPROCESSABLE_CONTENT, error.getMessage(), request);
    }

    @ExceptionHandler(DataAccessException.class)
    ProblemDetail unavailable(DataAccessException error, HttpServletRequest request) {
        // Keep the client response generic while retaining the exception in server-side searchable logs.
        LOG.atError()
                .addKeyValue("event", "data.store.unavailable")
                .addKeyValue("path", request.getRequestURI())
                .setCause(error)
                .log("Data store operation failed");
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "The data store is temporarily unavailable", request);
    }

    private static ProblemDetail problem(HttpStatus status, String detail, HttpServletRequest request) {
        var problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(status.getReasonPhrase());
        problem.setInstance(URI.create(request.getRequestURI()));
        return problem;
    }
}
