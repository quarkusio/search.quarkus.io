package io.quarkus.search.app.fetching;

import static io.quarkus.search.app.util.UncheckedIOFunction.uncheckedIO;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.function.Consumer;

import jakarta.inject.Inject;

import io.quarkus.search.app.entity.Guide;
import io.quarkus.search.app.hibernate.InputProvider;
import io.quarkus.search.app.quarkusio.QuarkusIO;
import io.quarkus.search.app.testsupport.GitTestUtils;
import io.quarkus.search.app.util.CloseableDirectory;

import io.quarkus.test.component.QuarkusComponentTestExtension;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import org.apache.commons.io.file.PathUtils;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.assertj.core.api.SoftAssertions;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.revwalk.RevCommit;

class FetchingServiceTest {

    // Unfortunately we can't use @TempDir here,
    // because we need the path initialized before we create the extension below.
    static CloseableDirectory tmpDir;
    static {
        try {
            tmpDir = CloseableDirectory.temp("fetching-service-test");
        } catch (IOException e) {
            throw new RuntimeException("Could not init temp directory: " + e.getMessage(), e);
        }
    }

    @BeforeAll
    static void initOrigin() throws IOException, GitAPIException {
        Path sourceRepoPath = tmpDir.path();
        Path metadata1ToFetch = sourceRepoPath.resolve("_data/versioned/latest/index/quarkus.yaml");
        Path metadata2ToFetch = sourceRepoPath.resolve("_data/guides-2-7.yaml");
        Path guide1HtmlToFetch = sourceRepoPath.resolve("guides/" + FETCHED_GUIDE_1_NAME + ".html");
        Path guide2HtmlToFetch = sourceRepoPath.resolve("version/2.7/guides/" + FETCHED_GUIDE_2_NAME + ".html");
        try (Git git = Git.init().setDirectory(sourceRepoPath.toFile())
                .setInitialBranch(QuarkusIO.PAGES_BRANCH).call()) {
            GitTestUtils.cleanGitUserConfig();

            RevCommit initialCommit = git.commit().setMessage("Initial commit")
                    .setAllowEmpty(true)
                    .call();

            PathUtils.createParentDirectories(guide1HtmlToFetch);
            Files.writeString(guide1HtmlToFetch, "initial");
            git.add().addFilepattern(".").call();
            git.commit().setMessage("Pages first commit").call();

            Files.writeString(guide1HtmlToFetch, FETCHED_GUIDE_1_CONTENT_HTML);
            PathUtils.createParentDirectories(guide2HtmlToFetch);
            Files.writeString(guide2HtmlToFetch, FETCHED_GUIDE_2_CONTENT_HTML);
            git.add().addFilepattern(".").call();
            git.commit().setMessage("Pages second commit").call();

            git.checkout()
                    .setName(QuarkusIO.SOURCE_BRANCH)
                    .setCreateBranch(true)
                    .setStartPoint(initialCommit)
                    .call();

            PathUtils.createParentDirectories(metadata1ToFetch);
            Files.writeString(metadata1ToFetch, METADATA_YAML);
            PathUtils.createParentDirectories(metadata2ToFetch);
            Files.writeString(metadata2ToFetch, METADATA_LEGACY_YAML);
            git.add().addFilepattern(".").call();
            git.commit().setMessage("Source first commit").call();
        }
    }

    @AfterAll
    static void deleteTmpDir() throws IOException {
        if (tmpDir != null) {
            tmpDir.close();
        }
    }

    @RegisterExtension
    static final QuarkusComponentTestExtension extension = QuarkusComponentTestExtension.builder()
            .configProperty("quarkusio.git-uri", tmpDir.path().toString())
            .build();

    @Inject
    FetchingService service;

    @Test
    void fetchQuarkusIo() throws Exception {
        try (QuarkusIO quarkusIO = service.fetchQuarkusIo()) {
            try (var guides = quarkusIO.guides()) {
                assertThat(guides)
                        .hasSize(6)
                        .satisfiesExactly(
                                isGuide("https://quarkus.io/guides/" + FETCHED_GUIDE_1_NAME,
                                        "Some title",
                                        "This is a summary",
                                        "keyword1 keyword2",
                                        Set.of("category1", "category2"),
                                        Set.of("topic1", "topic2"),
                                        Set.of("io.quarkus:extension1", "io.quarkus:extension2"),
                                        FETCHED_GUIDE_1_CONTENT_HTML),
                                isLocalizedGuide("https://es.quarkus.io/guides/" + FETCHED_GUIDE_1_NAME),
                                isLocalizedGuide("https://pt.quarkus.io/guides/" + FETCHED_GUIDE_1_NAME),
                                isLocalizedGuide("https://cn.quarkus.io/guides/" + FETCHED_GUIDE_1_NAME),
                                isLocalizedGuide("https://ja.quarkus.io/guides/" + FETCHED_GUIDE_1_NAME),
                                isGuide("https://quarkus.io/version/2.7/guides/" + FETCHED_GUIDE_2_NAME,
                                        "Some other title",
                                        "This is a different summary.",
                                        null,
                                        Set.of("getting-started"),
                                        Set.of(),
                                        Set.of(),
                                        FETCHED_GUIDE_2_CONTENT_HTML));
            }
        }
    }

    private static final String METADATA_YAML = """
            # Generated file. Do not edit
            ---
            types:
              reference:
              - title: Some title
                filename: foo.adoc
                summary: This is a summary
                categories: "category1, category2"
                keywords: keyword1 keyword2
                topics:
                - topic1
                - topic2
                extensions:
                - io.quarkus:extension1
                - io.quarkus:extension2
                id: foo
                type: reference
                url: /guides/foo
            """;

    private static final String METADATA_LEGACY_YAML = """
            # Generated file. Do not edit
            ---
            categories:
              - category: Getting Started
                cat-id: getting-started
                guides:
                  - title: Some other title
                    url: /guides/bar
                    description: This is a different summary.
            """;

    private static final String FETCHED_GUIDE_1_NAME = "foo";
    private static final String FETCHED_GUIDE_1_CONTENT_HTML = """
            <html>
            <head></head>
            <body>
            <h1>Some title</h1>
            <p>This is the guide body
            <h2>Some subsection</h2>
            This is a subsection
            <h2>Some other subsection</h2>
            This is another subsection
            """;
    private static final String FETCHED_GUIDE_2_NAME = "bar";
    private static final String FETCHED_GUIDE_2_CONTENT_HTML = """
            <html>
            <head></head>
            <body>
            <h1>Some other title</h1>
            <p>This is the other guide body
            """;

    private static Consumer<Guide> isLocalizedGuide(String url) {
        return guide -> {
            SoftAssertions.assertSoftly(softly -> {
                softly.assertThat(guide).extracting("url").asString().isEqualTo(url);
            });
        };
    }

    private static Consumer<Guide> isGuide(String url, String title, String summary, String keywords,
            Set<String> categories, Set<String> topics, Set<String> extensions, String content) {
        return guide -> {
            SoftAssertions.assertSoftly(softly -> {
                softly.assertThat(guide).extracting("url").asString().isEqualTo(url);
                softly.assertThat(guide).extracting("title").isEqualTo(title);
                softly.assertThat(guide).extracting("summary").isEqualTo(summary);
                softly.assertThat(guide).extracting("keywords").isEqualTo(keywords);
                softly.assertThat(guide).extracting("categories", InstanceOfAssertFactories.COLLECTION)
                        .containsExactlyInAnyOrderElementsOf(categories);
                softly.assertThat(guide).extracting("topics", InstanceOfAssertFactories.COLLECTION)
                        .containsExactlyInAnyOrderElementsOf(topics);
                softly.assertThat(guide).extracting("extensions", InstanceOfAssertFactories.COLLECTION)
                        .containsExactlyInAnyOrderElementsOf(extensions);
                softly.assertThat(guide)
                        .extracting("htmlFullContentProvider", InstanceOfAssertFactories.type(InputProvider.class))
                        .extracting(uncheckedIO(InputProvider::open), InstanceOfAssertFactories.INPUT_STREAM)
                        .hasContent(content);
            });
        };
    }
}
