package com.transactionapi.error;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import jakarta.validation.ConstraintViolationException;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.util.StringUtils;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleUnreadableMessage(HttpMessageNotReadableException ex) {
        return badRequest(resolveUnreadableMessage(ex));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        List<String> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(this::formatFieldError)
                .toList();
        return badRequest(errors.isEmpty() ? "Validation failed" : String.join("; ", errors), errors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConstraintViolation(ConstraintViolationException ex) {
        List<String> errors = ex.getConstraintViolations().stream()
                .map(violation -> fieldLabel(lastPathSegment(violation.getPropertyPath().toString()))
                        + " "
                        + violation.getMessage())
                .toList();
        return badRequest(errors.isEmpty() ? "Validation failed" : String.join("; ", errors), errors);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return badRequest(fieldLabel(ex.getName()) + " " + requiredTypeMessage(ex.getRequiredType()));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, Object>> handleMissingParameter(MissingServletRequestParameterException ex) {
        return badRequest(fieldLabel(ex.getParameterName()) + " is required");
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatus(ResponseStatusException ex) {
        HttpStatusCode status = ex.getStatusCode();
        String message = ex.getReason();
        if (!StringUtils.hasText(message)) {
            if (status instanceof HttpStatus httpStatus) {
                message = httpStatus.getReasonPhrase();
            } else {
                message = "Request failed";
            }
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", status.value());
        body.put("message", message);

        return ResponseEntity.status(status).body(body);
    }

    private ResponseEntity<Map<String, Object>> badRequest(String message) {
        return badRequest(message, List.of());
    }

    private ResponseEntity<Map<String, Object>> badRequest(String message, List<String> errors) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", HttpStatus.BAD_REQUEST.value());
        body.put("message", message);
        if (!errors.isEmpty()) {
            body.put("errors", errors);
        }
        return ResponseEntity.badRequest().body(body);
    }

    private String formatFieldError(FieldError error) {
        String message = error.getDefaultMessage();
        return fieldLabel(error.getField()) + " " + (StringUtils.hasText(message) ? message : "is invalid");
    }

    private String resolveUnreadableMessage(HttpMessageNotReadableException ex) {
        Throwable cause = ex.getMostSpecificCause();
        if (cause instanceof InvalidFormatException invalidFormat) {
            String fieldName = pathFieldName(invalidFormat);
            if (StringUtils.hasText(fieldName)) {
                return fieldLabel(fieldName) + " has an invalid value";
            }
        }
        if (cause instanceof MismatchedInputException mismatchedInput) {
            String fieldName = pathFieldName(mismatchedInput);
            if (StringUtils.hasText(fieldName)) {
                return fieldLabel(fieldName) + " has an invalid value";
            }
        }
        return "Request body is invalid or malformed";
    }

    private String pathFieldName(JsonMappingException exception) {
        List<JsonMappingException.Reference> path = exception.getPath();
        if (path == null || path.isEmpty()) {
            return null;
        }
        for (int i = path.size() - 1; i >= 0; i--) {
            String fieldName = path.get(i).getFieldName();
            if (StringUtils.hasText(fieldName)) {
                return fieldName;
            }
        }
        return null;
    }

    private String requiredTypeMessage(Class<?> requiredType) {
        if (requiredType == null) {
            return "has an invalid value";
        }
        Set<Class<?>> integerTypes = new LinkedHashSet<>(List.of(
                Byte.class,
                Short.class,
                Integer.class,
                Long.class,
                byte.class,
                short.class,
                int.class,
                long.class
        ));
        Set<Class<?>> decimalTypes = new LinkedHashSet<>(List.of(
                Float.class,
                Double.class,
                float.class,
                double.class,
                java.math.BigDecimal.class
        ));
        if (requiredType == java.util.UUID.class) {
            return "must be a valid UUID";
        }
        if (integerTypes.contains(requiredType)) {
            return "must be a valid whole number";
        }
        if (decimalTypes.contains(requiredType)) {
            return "must be a valid number";
        }
        if (requiredType == Boolean.class || requiredType == boolean.class) {
            return "must be true or false";
        }
        if (requiredType.isEnum()) {
            return "has an invalid value";
        }
        return "has an invalid value";
    }

    private String fieldLabel(String field) {
        if (!StringUtils.hasText(field)) {
            return "Request";
        }
        String[] words = field.replaceAll("([a-z])([A-Z])", "$1 $2").split("[._\\-\\s]+");
        return java.util.Arrays.stream(words)
                .filter(StringUtils::hasText)
                .map(word -> word.substring(0, 1).toUpperCase() + word.substring(1))
                .collect(java.util.stream.Collectors.joining(" "));
    }

    private String lastPathSegment(String path) {
        if (!StringUtils.hasText(path)) {
            return "";
        }
        int lastDot = path.lastIndexOf('.');
        return lastDot >= 0 ? path.substring(lastDot + 1) : path;
    }
}
