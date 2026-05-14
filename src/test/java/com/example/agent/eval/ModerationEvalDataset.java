package com.example.agent.eval;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public final class ModerationEvalDataset {

    public static final String EVAL_CASES_RESOURCE = "/moderation_eval_cases.json";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private ModerationEvalDataset() {
    }

    public static List<ModerationEvalCase> loadCases() throws IOException {
        try (InputStream inputStream = ModerationEvalDataset.class.getResourceAsStream(EVAL_CASES_RESOURCE)) {
            assertNotNull(inputStream, EVAL_CASES_RESOURCE + " not found");
            return OBJECT_MAPPER.readValue(inputStream, new TypeReference<>() {
            });
        }
    }
}
