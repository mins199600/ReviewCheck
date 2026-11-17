package com.moredr.reviewcheck.interview;

/** 사용자에게 그대로 보여줄 수 있는 메시지를 담는 예외 */
public class InterviewException extends RuntimeException {
    public InterviewException(String message) {
        super(message);
    }
}
