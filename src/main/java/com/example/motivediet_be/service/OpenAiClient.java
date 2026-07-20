package com.example.motivediet_be.service;

import com.example.motivediet_be.domain.IntensityLevel;
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
import org.springframework.util.StringUtils;
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
    private static final String COACH_MODEL = "gpt-5";
    private static final String SYSTEM_PROMPT = """
            너는 다이어트 앱의 동기 파싱기다. 사용자의 다이어트 동기 자유텍스트에서 구조화 신호만 뽑아라.
            - motiveType: 동기의 이벤트 유형
            - target: 동기와 관련된 대상이나 이벤트를 한국어 짧은 라벨로 (예: "여자친구 생일", "제주도 여행"). 홈 화면 칩에 그대로 노출된다
            - eventDate: 관련 날짜가 명확하면 YYYY-MM-DD, 없으면 null
            - paraphrase: 원문을 그대로 복원할 수 없는 수준으로 요약한 1문장 (예: "여자친구 생일 전까지 살 빼기")
            원문을 그대로 반복하지 마라.""";

    // 콘텐츠 가이드라인(a~d)을 시스템 프롬프트에 고정 삽입 — ROADMAP Phase 2, PRD 7.
    private static final String COACH_SYSTEM_PROMPT = """
            너는 다이어트 앱의 팩폭 코치다. 사용자가 방금 기록한 음식을 소재로 짧고 유머러스한 한국어 팩폭 한 문장을 만든다.
            반드시 지키는 규칙:
            (a) 외모·체중·타고난 능력을 공격하지 마라. 오직 먹는 행동(식습관)만 유머의 소재로 삼는다.
            (b) 자해·자살·섭식장애를 언급하거나 암시하지 마라.
            (c) 특정 집단(성별·지역·인종 등)을 비하하지 마라.
            (d) 욕설·혐오 표현을 쓰지 마라.
            강도 지침: MILD는 가볍게 툭 치는 정도, MEDIUM은 확실히 뼈 때리는 정도, STRONG은 강하게 몰아붙이되 위 4규칙은 절대 어기지 않는다.""";

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

    /**
     * 팩폭 코칭 메시지 생성 (gpt-5). 프롬프트 조합 규칙은 CoachMessageService가 정하고,
     * 여기는 넘겨받은 컨텍스트를 Structured Outputs로 강제해 {text, motiveComboText}만 받아온다.
     * daysUntil이 null이면 motiveComboText도 null을 반환하도록 프롬프트에서 지시한다.
     */
    public CoachResult generateCoachMessage(String categoryName, IntensityLevel intensity,
                                            String motiveTarget, String motiveParaphrase, Integer daysUntil) {
        String content = postForContent(buildCoachRequest(
                categoryName, intensity, motiveTarget, motiveParaphrase, daysUntil), "팩폭 생성");
        CoachResult result = gson.fromJson(content, CoachResult.class);
        // 스키마가 빈 문자열을 막지 못하므로(OpenAI Structured Outputs는 minLength 미지원) 여기서 방어한다.
        if (result == null || !StringUtils.hasText(result.text())) {
            throw new RuntimeException("팩폭 생성 결과를 해석하지 못했습니다.");
        }
        return result;
    }

    public record CoachResult(String text, String motiveComboText) {
    }

    // 헤더+POST+choices/content 추출. 200이어도 refusal/미완성이면 NPE 대신 통제된 예외로 바꾼다.
    private String postForContent(Map<String, Object> requestBody, String label) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        ResponseEntity<String> response = restTemplate.postForEntity(
                CHAT_URL, new HttpEntity<>(gson.toJson(requestBody), headers), String.class);
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException(label + " LLM 호출에 실패했습니다.");
        }

        JsonObject root = gson.fromJson(response.getBody(), JsonObject.class);
        JsonArray choices = root == null ? null : root.getAsJsonArray("choices");
        if (choices == null || choices.isEmpty()) {
            throw new RuntimeException(label + " 응답에 choices 가 없습니다.");
        }
        JsonObject message = choices.get(0).getAsJsonObject().getAsJsonObject("message");
        JsonElement contentEl = message == null ? null : message.get("content");
        if (contentEl == null || contentEl.isJsonNull()) {
            throw new RuntimeException(label + " 응답에 content 가 없습니다(거부 또는 미완성).");
        }
        return contentEl.getAsString();
    }

    private Map<String, Object> buildCoachRequest(String categoryName, IntensityLevel intensity,
                                                  String motiveTarget, String motiveParaphrase, Integer daysUntil) {
        boolean hasMotive = motiveTarget != null || motiveParaphrase != null;

        StringBuilder ctx = new StringBuilder();
        ctx.append("방금 기록한 음식: ").append(categoryName).append("\n");
        ctx.append("코칭 강도: ").append(intensity.name()).append("\n");
        if (motiveTarget != null) {
            ctx.append("사용자의 다이어트 동기 대상: ").append(motiveTarget).append("\n");
        }
        if (motiveParaphrase != null) {
            ctx.append("동기 요약: ").append(motiveParaphrase).append("\n");
        }
        if (daysUntil != null) {
            ctx.append("그 동기까지 D-").append(daysUntil)
                    .append(". 이 동기와 남은 날짜(D-day)를 엮은 팩폭 한 문장을 반드시 motiveComboText 에 담아라(null 금지).\n");
        } else {
            ctx.append("motiveComboText 는 null 로 둬라.\n");
        }
        ctx.append("text 에는 음식과 강도에 맞는 팩폭 한 문장을 담아라.");
        // 콤보(motiveComboText)가 이미 동기를 다루는 D-day 경로에서는 text 까지 동기를 엮으면 중복된다.
        // 콤보가 없을 때(D-14 밖·날짜 없음)만 text 가 동기를 참조하게 한다.
        if (hasMotive && daysUntil == null) {
            ctx.append(" 위 다이어트 동기를 자연스럽게 엮어라.");
        }

        // daysUntil 이 있으면 motiveComboText 를 string-only 로 강제해 콤보가 조용히 누락되는 걸 막는다.
        Object comboType = daysUntil != null ? "string" : List.of("string", "null");
        Map<String, Object> schema = Map.of(
                "type", "object",
                "additionalProperties", false,
                "required", List.of("text", "motiveComboText"),
                "properties", Map.of(
                        "text", Map.of("type", "string"),
                        "motiveComboText", Map.of("type", comboType)));

        return Map.of(
                "model", COACH_MODEL,
                "messages", List.of(
                        Map.of("role", "system", "content", COACH_SYSTEM_PROMPT),
                        Map.of("role", "user", "content", ctx.toString())),
                "response_format", Map.of(
                        "type", "json_schema",
                        "json_schema", Map.of(
                                "name", "coach_message",
                                "strict", true,
                                "schema", schema)));
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
