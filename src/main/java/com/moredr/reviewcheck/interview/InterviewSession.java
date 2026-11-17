package com.moredr.reviewcheck.interview;

import com.anthropic.models.messages.MessageParam;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 면접 한 건. 지금은 메모리에만 저장된다. */
public class InterviewSession {

    public enum Speaker { INTERVIEWER, CANDIDATE }

    public record Turn(Speaker speaker, String text) {
    }

    private final String id;
    private final String repoName;
    private final String repoUrl;
    private final int filesRead;
    private final int readableFiles;
    private final int questionCount;
    private final LocalDateTime createdAt = LocalDateTime.now();

    /** Claude API에 그대로 다시 보내는 대화 기록 (thinking 블록 포함) */
    private final List<MessageParam> apiMessages = new ArrayList<>();
    /** 화면에 보여줄 대화 기록 */
    private final List<Turn> transcript = new ArrayList<>();

    private int answeredCount = 0;
    private boolean finished = false;

    public InterviewSession(String id, String repoName, String repoUrl, int filesRead, int readableFiles,
                            int questionCount) {
        this.id = id;
        this.repoName = repoName;
        this.repoUrl = repoUrl;
        this.filesRead = filesRead;
        this.readableFiles = readableFiles;
        this.questionCount = questionCount;
    }

    public String getId() { return id; }
    public String getRepoName() { return repoName; }
    public String getRepoUrl() { return repoUrl; }
    public int getFilesRead() { return filesRead; }
    public int getReadableFiles() { return readableFiles; }
    public int getQuestionCount() { return questionCount; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public int getAnsweredCount() { return answeredCount; }
    public boolean isFinished() { return finished; }
    public List<Turn> getTranscript() { return Collections.unmodifiableList(transcript); }

    /** 지금 보고 있는 질문 번호 (1부터) */
    public int getCurrentQuestion() { return Math.min(answeredCount + 1, questionCount); }

    List<MessageParam> apiMessages() { return apiMessages; }
    void addTurn(Speaker speaker, String text) { transcript.add(new Turn(speaker, text)); }
    void markAnswered() { answeredCount++; }
    void finish() { finished = true; }
}
