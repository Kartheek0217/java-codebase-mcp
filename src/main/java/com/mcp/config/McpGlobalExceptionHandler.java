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
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.http.MediaType;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
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

	@ExceptionHandler(MissingRequestHeaderException.class)
	public ResponseEntity<Map<String, String>> handleMissingRequestHeaderException(MissingRequestHeaderException ex) {
		Map<String, String> error = new HashMap<>();
		error.put("error", "Missing required header: " + ex.getHeaderName());
		error.put("type", ex.getClass().getName());
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.contentType(MediaType.APPLICATION_JSON)
				.body(error);
	}

	@ExceptionHandler(ResponseStatusException.class)
	public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException ex) {
		Map<String, String> error = new HashMap<>();
		error.put("error", ex.getReason());
		error.put("type", ex.getClass().getName());
		return ResponseEntity.status(ex.getStatusCode())
				.contentType(MediaType.APPLICATION_JSON)
				.body(error);
	}

	@ResponseStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
	@ExceptionHandler(HttpMediaTypeNotSupportedException.class)
	public Map<String, String> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex) {
		Map<String, String> error = new HashMap<>();
		error.put("error", "Unsupported Media Type: " + ex.getMessage());
		error.put("type", ex.getClass().getName());
		return error;
	}

	@ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	public Map<String, String> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
		Map<String, String> error = new HashMap<>();
		error.put("error", "Method Not Allowed: " + ex.getMessage());
		error.put("type", ex.getClass().getName());
		return error;
	}

	@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
	@ExceptionHandler(Exception.class)
	public ResponseEntity<Map<String, String>> handleAllExceptions(Exception ex) {
		logger.error("Unhandled exception: ", ex);
		Map<String, String> error = new HashMap<>();
		error.put("error", ex.getMessage());
		error.put("type", ex.getClass().getName());
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.contentType(MediaType.APPLICATION_JSON)
				.body(error);
	}
}
