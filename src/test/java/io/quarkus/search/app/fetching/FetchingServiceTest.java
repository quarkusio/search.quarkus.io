package io.quarkus.search.app.fetching;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.function.Consumer;

import jakarta.inject.Inject;

import org.apache.commons.io.file.PathUtils;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.assertj.core.api.SoftAssertions;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import org.mockito.Mockito;

import io.quarkus.search.app.entity.Guide;
import io.quarkus.search.app.testsupport.GitTestUtils;
import io.quarkus.search.app.util.CloseableDirectory;
import io.quarkus.test.component.QuarkusComponentTestExtension;

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
        Path guide1ToFetch = sourceRepoPath.resolve("_guides/" + FETCHED_GUIDE_1_NAME + ".adoc");
        Path guide2ToFetch = sourceRepoPath.resolve("_versions/2.7/guides/" + FETCHED_GUIDE_2_NAME + ".adoc");
        Path adocToIgnore = sourceRepoPath.resolve("_guides/_attributes.adoc");
        try (Git git = Git.init().setDirectory(sourceRepoPath.toFile()).call()) {
            GitTestUtils.cleanGitUserConfig();

            PathUtils.createParentDirectories(guide1ToFetch);
            Files.writeString(guide1ToFetch, "initial");
            PathUtils.createParentDirectories(adocToIgnore);
            Files.writeString(adocToIgnore, "ignored");
            git.add().addFilepattern(".").call();
            git.commit().setMessage("First commit").call();

            Files.writeString(guide1ToFetch, FETCHED_GUIDE_1_CONTENT);
            PathUtils.createParentDirectories(guide2ToFetch);
            Files.writeString(guide2ToFetch, FETCHED_GUIDE_2_CONTENT);
            git.add().addFilepattern(".").call();
            git.commit().setMessage("Second commit").call();
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
            // It seems injecting config mappings isn't supported at the moment;
            // see https://quarkusio.zulipchat.com/#narrow/stream/187038-dev/topic/QuarkusComponentTest.20and.20ConfigMapping
            .mock(FetchingConfig.class)
            .createMockitoMock(mock -> {
                Mockito.when(mock.quarkusio())
                        .thenReturn(new FetchingConfig.Source() {
                            // We don't want to rely on external resources in tests,
                            // so we use a local git repo to simulate quarkus.io's git repository.
                            @Override
                            public URI uri() {
                                return tmpDir.path().toUri();
                            }
                        });
            })
            .build();

    @Inject
    FetchingService service;

    @Test
    void fetchQuarkusIo() throws Exception {
        try (QuarkusIO quarkusIO = service.fetchQuarkusIo()) {
            try (var guides = quarkusIO.guides()) {
                assertThat(guides)
                        .hasSize(2)
                        .satisfiesExactly(
                                isGuide("/guides/" + FETCHED_GUIDE_1_NAME,
                                        "Some title",
                                        "This is a summary",
                                        "keyword1, keyword2",
                                        Set.of("category1", "category2"),
                                        Set.of("topic1", "topic2"),
                                        Set.of("io.quarkus:extension1", "io.quarkus:extension2"),
                                        FETCHED_GUIDE_1_CONTENT),
                                isGuide("/version/2.7/guides/" + FETCHED_GUIDE_2_NAME,
                                        "Some other title",
                                        null,
                                        "keyword3, keyword4",
                                        Set.of(),
                                        Set.of("topic3", "topic4"),
                                        Set.of("io.quarkus:extension3"),
                                        FETCHED_GUIDE_2_CONTENT));
            }
        }
    }

    private static final String FETCHED_GUIDE_1_NAME = "foo";
    private static final String FETCHED_GUIDE_2_NAME = "bar";
    private static final String FETCHED_GUIDE_1_CONTENT = """
            = Some title
            :irrelevant: foo
            :categories: category1, category2
            :keywords: keyword1, keyword2
            :summary: This is a summary
            :topics: topic1, topic2
            :extensions: io.quarkus:extension1,io.quarkus:extension2

            This is the guide body

            == Some subsection
            :irrelevant2: foo

            This is a subsection

            == Some other subsection
            This is another subsection
            """;
    private static final String FETCHED_GUIDE_2_CONTENT = """
            = Some other title
            :keywords: keyword3, keyword4
            :topics: topic3, topic4
            :extensions: io.quarkus:extension3

            This is the other guide body
            """;

    private static Consumer<Guide> isGuide(String relativePath, String title, String summary, String keywords,
            Set<String> categories, Set<String> topics, Set<String> extensions, String content) {
        return guide -> {
            SoftAssertions.assertSoftly(softly -> {
                softly.assertThat(guide).extracting("relativePath").isEqualTo(relativePath);
                softly.assertThat(guide).extracting("title").isEqualTo(title);
                softly.assertThat(guide).extracting("summary").isEqualTo(summary);
                softly.assertThat(guide).extracting("keywords").isEqualTo(keywords);
                softly.assertThat(guide).extracting("categories", InstanceOfAssertFactories.COLLECTION)
                        .containsExactlyInAnyOrderElementsOf(categories);
                softly.assertThat(guide).extracting("topics", InstanceOfAssertFactories.COLLECTION)
                        .containsExactlyInAnyOrderElementsOf(topics);
                softly.assertThat(guide).extracting("extensions", InstanceOfAssertFactories.COLLECTION)
                        .containsExactlyInAnyOrderElementsOf(extensions);
                softly.assertThat(guide).extracting("fullContentPath.value", InstanceOfAssertFactories.PATH)
                        .content()
                        .isEqualTo(content);
            });
        };
    }
}
