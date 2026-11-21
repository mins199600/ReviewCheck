package com.moredr.reviewcheck.interview;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UsageGuardTest {

    @Test
    void blocksEveryoneWhenNoAccessCodeConfigured() {
        UsageGuard guard = new UsageGuard("", 3, 20);
        assertThrows(InterviewException.class, () -> guard.checkAndCountStart("1.1.1.1", ""));
        assertThrows(InterviewException.class, () -> guard.checkAndCountStart("1.1.1.1", "anything"));
    }

    @Test
    void rejectsWrongCodeAndAcceptsRightCode() {
        UsageGuard guard = new UsageGuard("secret-code", 3, 20);
        assertThrows(InterviewException.class, () -> guard.checkAndCountStart("1.1.1.1", "wrong"));
        assertThrows(InterviewException.class, () -> guard.checkAndCountStart("1.1.1.1", null));
        assertDoesNotThrow(() -> guard.checkAndCountStart("1.1.1.1", "secret-code"));
    }

    @Test
    void limitsStartsPerIp() {
        UsageGuard guard = new UsageGuard("c", 2, 20);
        guard.checkAndCountStart("1.1.1.1", "c");
        guard.checkAndCountStart("1.1.1.1", "c");
        assertThrows(InterviewException.class, () -> guard.checkAndCountStart("1.1.1.1", "c"));
        assertDoesNotThrow(() -> guard.checkAndCountStart("2.2.2.2", "c"));
    }

    @Test
    void limitsStartsPerDayAcrossAllIps() {
        UsageGuard guard = new UsageGuard("c", 5, 2);
        guard.checkAndCountStart("1.1.1.1", "c");
        guard.checkAndCountStart("2.2.2.2", "c");
        assertThrows(InterviewException.class, () -> guard.checkAndCountStart("3.3.3.3", "c"));
    }

    @Test
    void wrongCodeDoesNotUseUpQuota() {
        UsageGuard guard = new UsageGuard("c", 1, 20);
        assertThrows(InterviewException.class, () -> guard.checkAndCountStart("1.1.1.1", "x"));
        assertDoesNotThrow(() -> guard.checkAndCountStart("1.1.1.1", "c"));
    }
}
