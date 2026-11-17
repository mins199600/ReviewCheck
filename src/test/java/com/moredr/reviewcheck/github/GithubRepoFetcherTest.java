package com.moredr.reviewcheck.github;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GithubRepoFetcherTest {

    @Test
    void parsesPlainRepoUrl() {
        GithubRepoFetcher.RepoRef ref = GithubRepoFetcher.parse("https://github.com/spring-projects/spring-petclinic");
        assertEquals("spring-projects", ref.owner());
        assertEquals("spring-petclinic", ref.repo());
        assertNull(ref.branch());
    }

    @Test
    void parsesGitSuffixTrailingSlashAndBranch() {
        assertEquals("petclinic", GithubRepoFetcher.parse("https://github.com/a/petclinic.git").repo());
        assertEquals("petclinic", GithubRepoFetcher.parse("github.com/a/petclinic/").repo());
        GithubRepoFetcher.RepoRef ref = GithubRepoFetcher.parse("https://github.com/a/b/tree/dev/src/main");
        assertEquals("b", ref.repo());
        assertEquals("dev", ref.branch());
    }

    @Test
    void rejectsNonGithubUrl() {
        assertThrows(RepoFetchException.class, () -> GithubRepoFetcher.parse("https://gitlab.com/a/b"));
        assertThrows(RepoFetchException.class, () -> GithubRepoFetcher.parse("https://github.com/onlyowner"));
    }
}
