package com.mcp.controller;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;

import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mcp.dto.ExtractionRequestDTO;
import com.mcp.dto.ExtractionResultDTO;
import com.mcp.service.DataExtractionService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@Validated
@RestController
@RequestMapping("/api/web/extract")
@Tag(name = "Data Extraction", description = "Endpoints for extracting structured data from web pages")
public class DataExtractionController {

	private final DataExtractionService extractionService;
	private final AsyncTaskExecutor taskExecutor;
	private final Semaphore rateLimiter = new Semaphore(10);

	public DataExtractionController(DataExtractionService extractionService,
			@org.springframework.beans.factory.annotation.Qualifier("applicationTaskExecutor") AsyncTaskExecutor taskExecutor) {
		this.extractionService = extractionService;
		this.taskExecutor = taskExecutor;
	}

	@PostMapping
	@Operation(summary = "extract", description = "Extract data from a URL using selectors")
	public CompletableFuture<ExtractionResultDTO> extract(@Valid @RequestBody ExtractionRequestDTO request) {
		return CompletableFuture.supplyAsync(() -> {
			try {
				if (!rateLimiter.tryAcquire(5, java.util.concurrent.TimeUnit.SECONDS)) {
					return new ExtractionResultDTO(request.url(), null, "FAILED: Rate limit exceeded or server busy");
				}
				try {
					return extractionService.extract(request);
				} finally {
					rateLimiter.release();
				}
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return new ExtractionResultDTO(request.url(), null, "FAILED: Request interrupted");
			}
		}, taskExecutor);
	}

	@GetMapping("/metadata")
	@Operation(summary = "extract-metadata", description = "Extract page metadata (title, description, OG tags)")
	public CompletableFuture<ResponseEntity<Map<String, String>>> extractMetadata(@RequestParam String url) {
		return CompletableFuture.supplyAsync(() -> {
			try {
				if (!rateLimiter.tryAcquire(5, java.util.concurrent.TimeUnit.SECONDS)) {
					return ResponseEntity.status(429).build();
				}
				try {
					return ResponseEntity.ok(extractionService.extractMetadata(url));
				} finally {
					rateLimiter.release();
				}
			} catch (IOException e) {
				return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return ResponseEntity.status(500).body(Map.of("error", "Request interrupted"));
			}
		}, taskExecutor);
	}
}
