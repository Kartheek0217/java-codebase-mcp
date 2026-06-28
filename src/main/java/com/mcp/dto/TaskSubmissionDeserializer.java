package com.mcp.dto;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.List;

public class TaskSubmissionDeserializer extends JsonDeserializer<TaskSubmission> {
    @Override
    public TaskSubmission deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        ObjectMapper mapper = (ObjectMapper) p.getCodec();
        JsonNode node = mapper.readTree(p);

        if (node.isArray()) {
            List<BatchTaskRequest> list = mapper.convertValue(node, new TypeReference<List<BatchTaskRequest>>() {});
            return new TaskSubmission(list);
        } else if (node.isObject()) {
            if (node.has("body") && node.get("body").isArray()) {
                List<BatchTaskRequest> list = mapper.convertValue(node.get("body"), new TypeReference<List<BatchTaskRequest>>() {});
                return new TaskSubmission(list);
            } else {
                BatchTaskRequest req = mapper.convertValue(node, BatchTaskRequest.class);
                return new TaskSubmission(List.of(req));
            }
        }
        throw new IOException("Invalid task submission format: expected array, object with body array, or single task object");
    }
}
