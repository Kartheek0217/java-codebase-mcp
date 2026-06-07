package com.mcp.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Data Transfer Object (DTO) representing the response payload for a unified Large Language Model (LLM) action.
 * <p>
 * Encapsulates the project context, the specific model utilized, and the generated content
 * or answer resulting from the LLM invocation.
 *
 * @param projectId the unique identifier of the project associated with the action
 * @param model     the default or resolved LLM model used to execute the action
 * @param answer    the generated content, code review, or answer produced by the LLM
 *
 * @author karthik.j
 */
@Schema(description = "Response payload for unified LLM action")
public record LlmActionResponse(

        @Schema(description = "ID of the project", example = "1")
        Long projectId,

        @Schema(description = "The default/resolved model used for the action", example = "DeepSeek-V4-Flash")
        String model,

        @Schema(description = "The answer, review, or generated content from LLM", example = "## Code Review...")
        String answer
) {
}