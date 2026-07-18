package com.example.motivediet_be.service;

import com.example.motivediet_be.domain.MotiveType;
import com.example.motivediet_be.dto.MotiveParseResult;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 이 프로젝트의 모든 LLM 호출을 통과시키는 공용 클라이언트 (벤더 하나, API 키 하나).
 * Phase 1은 동기 파싱(gpt-5-mini + Structured Outputs)만 쓴다. 팩폭 생성(gpt-5)은 Phase 2.
 */
@Service
public class OpenAiClient {

    private static final String CHAT_URL = "https://api.openai.com/v1/chat/completions";
    private static final String PARSE_MODEL = "gpt-5-mini";
    private static final String SYSTEM_PROMPT = """
            너는 다이어트 앱의 동기 파싱기다. 사용자의 다이어트 동기 자유텍스트에서 구조화 신호만 뽑아라.
            - motiveType: 동기의 이벤트 유형
            - target: 동기와 관련된 대상이나 이벤트를 한국어 짧은 라벨로 (예: "여자친구 생일", "제주도 여행"). 홈 화면 칩에 그대로 노출된다
            - eventDate: 관련 날짜가 명확하면 YYYY-MM-DD, 없으면 null
            - paraphrase: 원문을 그대로 복원할 수 없는 수준으로 요약한 1문장 (예: "여자친구 생일 전까지 살 빼기")
            원문을 그대로 반복하지 마라.""";

    private final Gson gson = new Gson();
    private final RestTemplate restTemplate;
    private final String apiKey;

    public OpenAiClient(@Value("${openai.api-key}") String apiKey) {
        this.apiKey = apiKey;
        // 타임아웃이 없으면 OpenAI 가 응답을 안 줄 때 온보딩 트랜잭션·DB 커넥션이 무기한 점유된다.
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(30_000);
        this.restTemplate = new RestTemplate(factory);
    }

    public MotiveParseResult parseMotive(String motiveText) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        String body = gson.toJson(buildParseRequest(motiveText));
        ResponseEntity<String> response = restTemplate.postForEntity(
                CHAT_URL, new HttpEntity<>(body, headers), String.class);

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("동기 파싱 LLM 호출에 실패했습니다.");
        }

        // 200이어도 refusal/미완성(content 부재) 응답이 올 수 있다. 곧장 getAsString() 하면
        // NPE로 새므로 통제된 예외로 바꾼다. 이 예외는 온보딩 @Transactional 에서 롤백된다.
        JsonObject root = gson.fromJson(response.getBody(), JsonObject.class);
        JsonArray choices = root == null ? null : root.getAsJsonArray("choices");
        if (choices == null || choices.isEmpty()) {
            throw new RuntimeException("동기 파싱 응답에 choices 가 없습니다.");
        }
        JsonObject message = choices.get(0).getAsJsonObject().getAsJsonObject("message");
        JsonElement contentEl = message == null ? null : message.get("content");
        if (contentEl == null || contentEl.isJsonNull()) {
            throw new RuntimeException("동기 파싱 응답에 content 가 없습니다(거부 또는 미완성).");
        }

        MotiveParseResult result = gson.fromJson(contentEl.getAsString(), MotiveParseResult.class);
        if (result == null || result.motiveType() == null) {
            throw new RuntimeException("동기 파싱 결과를 해석하지 못했습니다.");
        }
        return result;
    }

    private Map<String, Object> buildParseRequest(String motiveText) {
        List<String> motiveTypes = Arrays.stream(MotiveType.values()).map(Enum::name).toList();

        Map<String, Object> schema = Map.of(
                "type", "object",
                "additionalProperties", false,
                "required", List.of("motiveType", "target", "eventDate", "paraphrase"),
                "properties", Map.of(
                        "motiveType", Map.of("type", "string", "enum", motiveTypes),
                        "target", Map.of("type", "string"),
                        "eventDate", Map.of("type", List.of("string", "null")),
                        "paraphrase", Map.of("type", "string")));

        return Map.of(
                "model", PARSE_MODEL,
                "messages", List.of(
                        Map.of("role", "system", "content", SYSTEM_PROMPT),
                        Map.of("role", "user", "content", motiveText)),
                "response_format", Map.of(
                        "type", "json_schema",
                        "json_schema", Map.of(
                                "name", "motive_signal",
                                "strict", true,
                                "schema", schema)));
    }
}
