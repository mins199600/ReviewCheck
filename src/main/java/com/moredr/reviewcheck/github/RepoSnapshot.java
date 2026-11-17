package com.moredr.reviewcheck.github;

import java.util.List;

/**
 * @param totalFiles     레포 전체 파일 수
 * @param readableFiles  읽을 수 있는(코드/설정) 파일 수
 * @param files          실제로 내용을 읽어 AI에게 넘긴 파일
 * @param treeTruncated  GitHub가 파일 목록을 잘라서 준 경우(아주 큰 레포)
 */
public record RepoSnapshot(String owner, String repo, String branch, String description,
                           int totalFiles, int readableFiles, String treeListing,
                           List<RepoFile> files, boolean treeTruncated) {

    public String fullName() {
        return owner + "/" + repo;
    }

    public String toPromptText() {
        StringBuilder sb = new StringBuilder();
        sb.append("<repository name=\"").append(fullName()).append("\" branch=\"").append(branch).append("\">\n");
        if (description != null && !description.isBlank()) {
            sb.append("<description>").append(description).append("</description>\n");
        }
        sb.append("<file_tree>\n").append(treeListing).append("\n</file_tree>\n");
        for (RepoFile f : files) {
            sb.append("<file path=\"").append(f.path()).append("\">\n")
                    .append(f.content())
                    .append("\n</file>\n");
        }
        sb.append("</repository>");
        return sb.toString();
    }
}
