package ru.netology.cloud.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import ru.netology.cloud.dto.ErrorResponse;

import java.util.concurrent.atomic.AtomicInteger;

/** Переводит бизнес-исключения и ошибки Spring MVC в HTTP-ответы с телом {message, id}. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final AtomicInteger errorId = new AtomicInteger();

    @ExceptionHandler({
            InvalidCredentialsException.class,
            InvalidFileDataException.class,
            FileNotFoundInStorageException.class,
            DuplicateFileException.class
    })
    public ResponseEntity<ErrorResponse> businessError(RuntimeException e) {
        return error(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<ErrorResponse> invalidToken(InvalidTokenException e) {
        return error(HttpStatus.UNAUTHORIZED, e.getMessage());
    }

    @ExceptionHandler({
            MissingServletRequestParameterException.class,
            MissingServletRequestPartException.class,
            MethodArgumentNotValidException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class,
            MultipartException.class
    })
    public ResponseEntity<ErrorResponse> invalidInput(Exception e) {
        log.debug("Invalid request: {}", e.getMessage());
        return error(HttpStatus.BAD_REQUEST, "Error input data");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> methodNotAllowed(HttpRequestMethodNotSupportedException e) {
        return error(HttpStatus.METHOD_NOT_ALLOWED, e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> internal(Exception e) {
        ResponseEntity<ErrorResponse> response = error(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error");
        log.error("Unexpected error, id={}", response.getBody().id(), e);
        return response;
    }

    private ResponseEntity<ErrorResponse> error(HttpStatus status, String message) {
        ErrorResponse body = new ErrorResponse(message, errorId.incrementAndGet());
        if (status.is4xxClientError()) {
            log.warn("Client error status={}, id={}: {}", status.value(), body.id(), message);
        }
        return ResponseEntity.status(status).body(body);
    }
}
