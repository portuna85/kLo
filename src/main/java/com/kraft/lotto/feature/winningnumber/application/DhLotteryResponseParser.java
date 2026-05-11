package com.kraft.lotto.feature.winningnumber.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kraft.lotto.feature.winningnumber.domain.LottoCombination;
import com.kraft.lotto.feature.winningnumber.domain.WinningNumber;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;

class DhLotteryResponseParser {

    private final ObjectMapper objectMapper;

    DhLotteryResponseParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    Optional<WinningNumber> parse(int round, String body) {
        String trimmed = body == null ? "" : body.trim();
        if (trimmed.startsWith("<")) {
            throw new LottoApiClientException("response is not JSON (round=" + round + ")");
        }
        JsonNode node;
        try {
            node = objectMapper.readTree(body);
        } catch (Exception ex) {
            throw new LottoApiClientException("response parse failed (round=" + round + ")", ex);
        }
        String returnValue = requiredText(node, "returnValue", round);
        if (!"success".equalsIgnoreCase(returnValue)) {
            throw new LottoApiClientException("unexpected returnValue (round=" + round + ", returnValue=" + returnValue + ")");
        }
        try {
            requireFields(node, round, "drwNo", "drwNoDate", "drwtNo1", "drwtNo2", "drwtNo3",
                    "drwtNo4", "drwtNo5", "drwtNo6", "bnusNo", "firstWinamnt", "firstPrzwnerCo", "totSellamnt");
            int drwNo = requiredInt(node, "drwNo", round);
            if (drwNo != round) {
                throw new LottoApiClientException("round mismatch request=" + round + ", response=" + drwNo);
            }
            LocalDate drawDate = LocalDate.parse(node.path("drwNoDate").asText());
            List<Integer> mains = List.of(
                    requiredInt(node, "drwtNo1", round), requiredInt(node, "drwtNo2", round),
                    requiredInt(node, "drwtNo3", round), requiredInt(node, "drwtNo4", round),
                    requiredInt(node, "drwtNo5", round), requiredInt(node, "drwtNo6", round)
            );
            int bonus = requiredInt(node, "bnusNo", round);
            long firstPrize = requiredLong(node, "firstWinamnt", round);
            int firstWinners = requiredInt(node, "firstPrzwnerCo", round);
            long totalSales = requiredLong(node, "totSellamnt", round);
            long firstAccumAmount = optionalLong(node, "firstAccumamnt", round, 0L);
            return Optional.of(new WinningNumber(
                    drwNo, drawDate, new LottoCombination(mains), bonus, firstPrize, firstWinners,
                    totalSales, firstAccumAmount, body, LocalDateTime.now()
            ));
        } catch (DateTimeParseException | IllegalArgumentException | NullPointerException ex) {
            throw new LottoApiClientException("response transform failed (round=" + round + "): " + ex.getMessage(), ex);
        }
    }

    private static int requiredInt(JsonNode node, String fieldName, int round) {
        JsonNode value = node.get(fieldName);
        if (value == null || value.isNull()) {
            throw new LottoApiClientException("field missing (round=" + round + ", field=" + fieldName + ")");
        }
        if (!value.isIntegralNumber()) {
            throw new LottoApiClientException("field is not integral (round=" + round + ", field=" + fieldName + ")");
        }
        if (!value.canConvertToInt()) {
            throw new LottoApiClientException("field out of int range (round=" + round + ", field=" + fieldName + ")");
        }
        return value.asInt();
    }

    private static long optionalLong(JsonNode node, String fieldName, int round, long defaultValue) {
        if (node.path(fieldName).isMissingNode() || node.path(fieldName).isNull()) {
            return defaultValue;
        }
        return requiredLong(node, fieldName, round);
    }

    private static long requiredLong(JsonNode node, String fieldName, int round) {
        JsonNode value = node.get(fieldName);
        if (value == null || value.isNull()) {
            throw new LottoApiClientException("field missing (round=" + round + ", field=" + fieldName + ")");
        }
        if (!value.isIntegralNumber()) {
            throw new LottoApiClientException("field is not integral (round=" + round + ", field=" + fieldName + ")");
        }
        if (!value.canConvertToLong()) {
            throw new LottoApiClientException("field out of long range (round=" + round + ", field=" + fieldName + ")");
        }
        return value.asLong();
    }

    private static void requireFields(JsonNode node, int round, String... fieldNames) {
        for (String fieldName : fieldNames) {
            if (node.path(fieldName).isMissingNode() || node.path(fieldName).isNull()) {
                throw new LottoApiClientException("field missing (round=" + round + ", field=" + fieldName + ")");
            }
        }
    }

    private static String requiredText(JsonNode node, String fieldName, int round) {
        if (node.path(fieldName).isMissingNode() || node.path(fieldName).isNull()) {
            throw new LottoApiClientException("field missing (round=" + round + ", field=" + fieldName + ")");
        }
        return node.path(fieldName).asText();
    }
}
