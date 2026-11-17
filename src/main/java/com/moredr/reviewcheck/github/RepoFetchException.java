package com.moredr.reviewcheck.github;

/** 사용자에게 그대로 보여줄 수 있는 메시지를 담는 예외 */
public class RepoFetchException extends RuntimeException {
    public RepoFetchException(String message) {
        super(message);
    }
}
