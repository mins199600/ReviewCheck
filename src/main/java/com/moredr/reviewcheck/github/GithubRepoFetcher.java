package com.moredr.reviewcheck.github;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * GitHub 공개 레포에서 파일 목록과 주요 파일 내용을 가져온다.
 */
@Component
public class GithubRepoFetcher {

    private static final Pattern REPO_URL = Pattern.compile(
            "^(?:https?://)?(?:www\\.)?github\\.com/([\\w.-]+)/([\\w.-]+?)(?:\\.git)?(?:/tree/([^/?#]+))?(?:[/?#].*)?$");

    private static final Set<String> SKIP_DIRS = Set.of(
            "node_modules", "build", "dist", "target", "out", "bin", "obj", ".gradle", ".idea", ".vscode",
            ".git", "vendor", "venv", ".venv", "__pycache__", ".next", "coverage", "gradle", ".mvn");

    private static final Set<String> SKIP_FILES = Set.of(
            "package-lock.json", "yarn.lock", "pnpm-lock.yaml", "gradlew", "gradlew.bat", "mvnw", "mvnw.cmd",
            "poetry.lock", "Cargo.lock", "go.sum", "composer.lock");

    private static final Set<String> CODE_EXT = Set.of(
            "java", "kt", "kts", "groovy", "scala", "py", "js", "jsx", "ts", "tsx", "vue", "svelte", "go", "rs",
            "rb", "php", "cs", "c", "h", "cpp", "hpp", "swift", "dart", "sql", "html", "css", "scss",
            "xml", "yml", "yaml", "properties", "json", "toml", "gradle", "md", "sh");

    private static final Set<String> KEY_FILES = Set.of(
            "build.gradle", "build.gradle.kts", "settings.gradle", "pom.xml", "package.json", "requirements.txt",
            "pyproject.toml", "go.mod", "cargo.toml", "dockerfile", "docker-compose.yml", "docker-compose.yaml",
            "application.properties", "application.yml", "application.yaml", "tsconfig.json", "vite.config.js",
            "vite.config.ts", "next.config.js");

    private static final long MAX_FILE_BYTES = 100_000;
    private static final int MAX_TREE_LINES = 400;

    private final RestClient rest;
    private final JsonMapper json = JsonMapper.builder().build();
    private final String token;
    private final int maxFiles;
    private final int maxChars;

    public GithubRepoFetcher(@Value("${github.token:}") String token,
                             @Value("${github.max-files:60}") int maxFiles,
                             @Value("${github.max-chars:200000}") int maxChars) {
        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(20));
        this.rest = RestClient.builder()
                .requestFactory(factory)
                .defaultHeader("User-Agent", "ReviewCheck-mock-interview")
                .build();
        this.token = token == null ? "" : token.trim();
        this.maxFiles = maxFiles;
        this.maxChars = maxChars;
    }

    public RepoSnapshot fetch(String repoUrl) {
        RepoRef ref = parse(repoUrl);

        JsonNode repoInfo = getJson(URI.create("https://api.github.com/repos/" + ref.owner() + "/" + ref.repo()));
        String branch = ref.branch() != null ? ref.branch() : repoInfo.path("default_branch").asString("main");
        String description = repoInfo.path("description").asString("");

        JsonNode tree = getJson(URI.create("https://api.github.com/repos/" + ref.owner() + "/" + ref.repo()
                + "/git/trees/" + UriUtils.encodePathSegment(branch, StandardCharsets.UTF_8) + "?recursive=1"));

        List<TreeEntry> blobs = new ArrayList<>();
        for (JsonNode node : tree.path("tree")) {
            if ("blob".equals(node.path("type").asString(""))) {
                blobs.add(new TreeEntry(node.path("path").asString(), node.path("size").asLong(0)));
            }
        }
        if (blobs.isEmpty()) {
            throw new RepoFetchException("레포에 파일이 없어요.");
        }

        List<TreeEntry> candidates = blobs.stream()
                .filter(this::isReadable)
                .sorted(Comparator.comparingInt(this::priority).thenComparing(TreeEntry::path))
                .toList();

        List<RepoFile> files = new ArrayList<>();
        int usedChars = 0;
        for (TreeEntry entry : candidates) {
            if (files.size() >= maxFiles || usedChars >= maxChars) {
                break;
            }
            String content = getRaw(ref, branch, entry.path());
            if (content == null || content.isBlank()) {
                continue;
            }
            if (usedChars + content.length() > maxChars) {
                continue; // 너무 큰 파일은 건너뛰고 다음 파일을 본다
            }
            files.add(new RepoFile(entry.path(), content));
            usedChars += content.length();
        }

        String treeListing = blobs.stream()
                .map(TreeEntry::path)
                .filter(p -> Arrays.stream(p.split("/")).noneMatch(SKIP_DIRS::contains))
                .limit(MAX_TREE_LINES)
                .collect(Collectors.joining("\n"));

        return new RepoSnapshot(ref.owner(), ref.repo(), branch, description, blobs.size(),
                candidates.size(), treeListing, files, tree.path("truncated").asBoolean(false));
    }

    static RepoRef parse(String repoUrl) {
        if (repoUrl == null) {
            throw new RepoFetchException("GitHub 링크를 입력해 주세요.");
        }
        Matcher m = REPO_URL.matcher(repoUrl.trim());
        if (!m.matches()) {
            throw new RepoFetchException("https://github.com/아이디/레포이름 형식의 링크를 넣어 주세요.");
        }
        return new RepoRef(m.group(1), m.group(2), m.group(3));
    }

    private boolean isReadable(TreeEntry e) {
        if (e.size() > MAX_FILE_BYTES) {
            return false;
        }
        String[] parts = e.path().split("/");
        for (int i = 0; i < parts.length - 1; i++) {
            if (SKIP_DIRS.contains(parts[i])) {
                return false;
            }
        }
        String name = parts[parts.length - 1];
        if (SKIP_FILES.contains(name) || name.endsWith(".min.js") || name.endsWith(".min.css")) {
            return false;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        if (KEY_FILES.contains(lower)) {
            return true;
        }
        int dot = lower.lastIndexOf('.');
        return dot > 0 && CODE_EXT.contains(lower.substring(dot + 1));
    }

    /** 낮을수록 먼저 읽는다: README → 빌드/설정 → 소스 → 테스트/기타 */
    private int priority(TreeEntry e) {
        String path = e.path();
        String lower = path.toLowerCase(Locale.ROOT);
        String name = lower.substring(lower.lastIndexOf('/') + 1);
        if (name.startsWith("readme")) {
            return path.contains("/") ? 1 : 0;
        }
        if (KEY_FILES.contains(name)) {
            return 1;
        }
        if (lower.contains("/test/") || lower.contains("__tests__") || lower.startsWith("test")
                || name.contains(".test.") || name.contains(".spec.")) {
            return 4;
        }
        if (name.endsWith(".md") || name.endsWith(".json") || name.endsWith(".html")
                || name.endsWith(".css") || name.endsWith(".scss")) {
            return 3;
        }
        return 2;
    }

    private JsonNode getJson(URI uri) {
        try {
            String body = rest.get().uri(uri)
                    .header("Accept", "application/vnd.github+json")
                    .headers(h -> {
                        if (!token.isEmpty()) {
                            h.setBearerAuth(token);
                        }
                    })
                    .retrieve()
                    .body(String.class);
            return json.readTree(body);
        } catch (HttpClientErrorException.NotFound e) {
            throw new RepoFetchException("레포를 찾을 수 없어요. 공개 레포인지, 링크가 맞는지 확인해 주세요.");
        } catch (HttpClientErrorException.Forbidden | HttpClientErrorException.TooManyRequests e) {
            throw new RepoFetchException("GitHub API 요청 한도를 넘었어요. 잠시 후 다시 하거나 GITHUB_TOKEN 환경변수를 설정해 주세요.");
        } catch (RestClientException e) {
            throw new RepoFetchException("GitHub에 연결하지 못했어요: " + e.getMessage());
        }
    }

    private String getRaw(RepoRef ref, String branch, String path) {
        String encodedPath = Arrays.stream(path.split("/"))
                .map(s -> UriUtils.encodePathSegment(s, StandardCharsets.UTF_8))
                .collect(Collectors.joining("/"));
        URI uri = URI.create("https://raw.githubusercontent.com/" + ref.owner() + "/" + ref.repo() + "/"
                + UriUtils.encodePathSegment(branch, StandardCharsets.UTF_8) + "/" + encodedPath);
        try {
            byte[] bytes = rest.get().uri(uri)
                    .headers(h -> {
                        if (!token.isEmpty()) {
                            h.setBearerAuth(token);
                        }
                    })
                    .retrieve()
                    .body(byte[].class);
            if (bytes == null) {
                return null;
            }
            for (int i = 0; i < Math.min(bytes.length, 1000); i++) {
                if (bytes[i] == 0) {
                    return null; // 바이너리 파일
                }
            }
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (RestClientException e) {
            return null; // 한 파일 실패는 건너뛴다
        }
    }

    record RepoRef(String owner, String repo, String branch) {
    }

    private record TreeEntry(String path, long size) {
    }
}
