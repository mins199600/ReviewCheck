package com.moredr.reviewcheck.interview;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;

/**
 * Claude API 비용이 새지 않도록 면접 시작을 막는 문지기.
 * - 접속 코드가 맞아야 시작할 수 있다. (코드가 설정되지 않았으면 아무도 시작할 수 없다)
 * - 같은 IP는 하루에 정해진 횟수까지만 시작할 수 있다.
 * - 서비스 전체로도 하루에 정해진 횟수까지만 시작할 수 있다.
 */
@Component
public class UsageGuard {

    private static final Logger log = LoggerFactory.getLogger(UsageGuard.class);
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final byte[] accessCode;
    private final int maxStartsPerIpPerDay;
    private final int maxStartsPerDay;

    private LocalDate today = LocalDate.now(SEOUL);
    private int startsToday = 0;
    private final Map<String, Integer> startsByIp = new HashMap<>();

    public UsageGuard(@Value("${interview.access-code:}") String accessCode,
                      @Value("${interview.max-starts-per-ip-per-day:3}") int maxStartsPerIpPerDay,
                      @Value("${interview.max-starts-per-day:20}") int maxStartsPerDay) {
        String code = accessCode == null ? "" : accessCode.trim();
        this.accessCode = code.getBytes(StandardCharsets.UTF_8);
        this.maxStartsPerIpPerDay = maxStartsPerIpPerDay;
        this.maxStartsPerDay = maxStartsPerDay;
        if (code.isEmpty()) {
            log.warn("INTERVIEW_ACCESS_CODE가 설정되지 않아 면접 시작이 모두 막혀 있습니다.");
        }
    }

    /** 면접을 시작해도 되는지 확인하고, 되면 사용 횟수를 1 올린다. */
    public synchronized void checkAndCountStart(String clientIp, String submittedCode) {
        if (accessCode.length == 0) {
            throw new InterviewException("지금은 면접을 시작할 수 없어요. (관리자: 접속 코드가 설정되지 않았어요)");
        }
        byte[] submitted = (submittedCode == null ? "" : submittedCode.trim()).getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(accessCode, submitted)) {
            log.warn("잘못된 접속 코드로 면접 시작 시도: ip={}", clientIp);
            throw new InterviewException("접속 코드가 올바르지 않아요.");
        }

        resetIfNewDay();
        if (startsToday >= maxStartsPerDay) {
            log.warn("하루 전체 면접 한도 도달: {}", startsToday);
            throw new InterviewException("오늘 진행할 수 있는 면접 수를 모두 사용했어요. 내일 다시 시도해 주세요.");
        }
        int byIp = startsByIp.getOrDefault(clientIp, 0);
        if (byIp >= maxStartsPerIpPerDay) {
            log.warn("IP별 하루 면접 한도 도달: ip={}", clientIp);
            throw new InterviewException("오늘은 면접을 " + maxStartsPerIpPerDay + "번까지 볼 수 있어요. 내일 다시 시도해 주세요.");
        }
        startsToday++;
        startsByIp.put(clientIp, byIp + 1);
        log.info("면접 시작 허용: ip={}, 오늘 전체={}/{}", clientIp, startsToday, maxStartsPerDay);
    }

    private void resetIfNewDay() {
        LocalDate now = LocalDate.now(SEOUL);
        if (!now.equals(today)) {
            today = now;
            startsToday = 0;
            startsByIp.clear();
        }
    }
}
