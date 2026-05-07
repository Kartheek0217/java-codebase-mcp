package com.mcp.indexing.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class StatusController {
    @GetMapping("/status")
    public Map<String, Object> status() {
        return Map.of("status", "ok");
    }
}

