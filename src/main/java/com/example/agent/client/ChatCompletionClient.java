package com.example.agent.client;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

public interface ChatCompletionClient {

    JsonNode complete(List<Map<String, Object>> messages, List<Map<String, Object>> tools) throws Exception;
}
