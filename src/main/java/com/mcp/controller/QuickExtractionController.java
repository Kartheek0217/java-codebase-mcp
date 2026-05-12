package com.mcp.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mcp.dto.QuickExtractionResult;
import com.mcp.service.QuickExtractionService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/web/extract")
@Tag(name = "Web Extraction", description = "On-demand data extraction from web pages")
public class QuickExtractionController {

    private final QuickExtractionService extractionService;

    public QuickExtractionController(QuickExtractionService extractionService) {
        this.extractionService = extractionService;
    }

    @GetMapping
    @Operation(summary = "Extract data from a URL using a headless browser")
    public QuickExtractionResult extract(@RequestParam Long projectId, @RequestParam String url) {
        return extractionService.extract(projectId, url);
    }
}
