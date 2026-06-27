package com.mcp.config;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class McpGlobalExceptionHandler {

	private static final Logger logger = LoggerFactory.getLogger(McpGlobalExceptionHandler.class);

	@ResponseStatus(HttpStatus.BAD_REQUEST)
	@ExceptionHandler(MethodArgumentNotValidException.class)
	public Map<String, String> handleValidationExceptions(MethodArgumentNotValidException ex) {
		Map<String, String> errors = new HashMap<>();
		ex.getBindingResult().getAllErrors().forEach((error) -> {
			String fieldName = ((FieldError) error).getField();
			String errorMessage = error.getDefaultMessage();
			errors.put(fieldName, errorMessage);
		});
		return errors;
	}

	@ExceptionHandler(ResponseStatusException.class)
	public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException ex) {
		Map<String, String> error = new HashMap<>();
		error.put("error", ex.getReason());
		error.put("type", ex.getClass().getName());
		return new ResponseEntity<>(error, ex.getStatusCode());
	}

	@ResponseStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
	@ExceptionHandler(org.springframework.web.HttpMediaTypeNotSupportedException.class)
	public Map<String, String> handleMediaTypeNotSupported(org.springframework.web.HttpMediaTypeNotSupportedException ex) {
		Map<String, String> error = new HashMap<>();
		error.put("error", "Unsupported Media Type: " + ex.getMessage());
		error.put("type", ex.getClass().getName());
		return error;
	}

	@ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
	@ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
	public Map<String, String> handleMethodNotSupported(org.springframework.web.HttpRequestMethodNotSupportedException ex) {
		Map<String, String> error = new HashMap<>();
		error.put("error", "Method Not Allowed: " + ex.getMessage());
		error.put("type", ex.getClass().getName());
		return error;
	}

	@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
	@ExceptionHandler(Exception.class)
	public Map<String, String> handleAllExceptions(Exception ex) {
		logger.error("Unhandled exception: ", ex);
		Map<String, String> error = new HashMap<>();
		error.put("error", ex.getMessage());
		error.put("type", ex.getClass().getName());
		return error;
	}
}
