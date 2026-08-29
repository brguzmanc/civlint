package com.bguzman.civlint.web;

import com.bguzman.civlint.support.JsonParseException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Translates failures into RFC 9457 problem details.
 *
 * <p>Messages state what was rejected and why, without echoing the rejected document back. Echoing
 * untrusted input into an error response is a way for that input to reach a place that might render
 * it, so the input itself is deliberately not included.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail handleNotFound(NotFoundException exception) {
        ProblemDetail detail =
                ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
        detail.setTitle("Not found");
        return detail;
    }

    @ExceptionHandler(JsonParseException.class)
    public ProblemDetail handleParseFailure(JsonParseException exception) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "The supplied document was rejected: " + exception.getMessage());
        detail.setTitle("Invalid document");
        return detail;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException exception) {
        ProblemDetail detail =
                ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
        detail.setTitle("Invalid request");
        return detail;
    }
}
