package com.mcp.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.util.List;

@JsonDeserialize(using = TaskSubmissionDeserializer.class)
public record TaskSubmission(List<BatchTaskRequest> requests) {}
