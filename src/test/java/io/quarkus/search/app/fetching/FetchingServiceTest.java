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
import org.eclipse.jgit.util.SystemReader;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import org.mockito.Mockito;

import io.quarkus.search.app.entity.Guide;
import io.quarkus.test.component.QuarkusComponentTestExtension;

public class FetchingServiceTest {

    private static final Logger LOG = Logger.getLogger(FetchingServiceTest.class);

    // Unfortunately we can't use @TempDir here,
    // because we need the path initialized before we create the extension below.
    static Path tmpDir;
    static {
        try {
            tmpDir = Files.createTempDirectory("search-fetching");
        } catch (IOException e) {
            throw new RuntimeException("Could not init temp directory: " + e.getMessage(), e);
        }
    }

    @AfterAll
    static void deleteTmpDir() throws IOException {
        PathUtils.deleteDirectory(tmpDir);
    }

    static abstract class AbstractTest {
        protected static QuarkusComponentTestExtension extension(FetchingConfig.Source.Method quarkusIOMethod,
                URI quarkusIOURI) {
            var extension = new QuarkusComponentTestExtension();
            // It seems injecting config mappings isn't supported at the moment;
            // see https://quarkusio.zulipchat.com/#narrow/stream/187038-dev/topic/QuarkusComponentTest.20and.20ConfigMapping
            extension.mock(FetchingConfig.class)
                    .createMockitoMock(mock -> {
                        Mockito.when(mock.quarkusio())
                                .thenReturn(new FetchingConfig.Source() {
                                    @Override
                                    public Method method() {
                                        return quarkusIOMethod;
                                    }

                                    @Override
                                    public URI uri() {
                                        return quarkusIOURI;
                                    }
                                });
                    });
            return extension;
        }

        @Inject
        FetchingService service;

        @Test
        public void fetchQuarkusIo() throws Exception {
            try (QuarkusIO quarkusIO = service.fetchQuarkusIo()) {
                try (var guides = quarkusIO.guides()) {
                    assertThat(guides)
                            .hasSize(1)
                            .first()
                            .satisfies(isGuide(
                                    "/guides/" + FETCHED_GUIDE_NAME,
                                    "Some title",
                                    "This is a summary",
                                    "keyword1, keyword2",
                                    Set.of("topic1", "topic2"),
                                    Set.of("io.quarkus:extension1", "io.quarkus:extension2"),
                                    FETCHED_GUIDE_CONTENT));
                }
            }
        }
    }

    @Nested
    class LocalDirectoryTest extends AbstractTest {
        static final Path sourceDir = tmpDir.resolve("local");

        @RegisterExtension
        static final QuarkusComponentTestExtension extension = extension(
                FetchingConfig.Source.Method.LOCAL,
                URI.create("file:" + sourceDir));

        @BeforeAll
        static void initLocalRepo() throws IOException {
            Path guideToFetch = sourceDir.resolve("_guides/" + FETCHED_GUIDE_NAME + ".adoc");
            Path adocToIgnore = sourceDir.resolve("_guides/_attributes.adoc");
            PathUtils.createParentDirectories(guideToFetch);
            Files.writeString(guideToFetch, FETCHED_GUIDE_CONTENT);
            PathUtils.createParentDirectories(adocToIgnore);
            Files.writeString(adocToIgnore, "ignored");
        }
    }

    @Nested
    class GitTest extends AbstractTest {
        static final Path sourceRepoDir = tmpDir.resolve("git");

        @RegisterExtension
        static final QuarkusComponentTestExtension extension = extension(
                FetchingConfig.Source.Method.GIT,
                // We don't want to rely on external resources in tests,
                // so we use a local git repo to simulate quarkus.io's git repository.
                URI.create("file:" + sourceRepoDir));

        @BeforeAll
        static void initOrigin() throws IOException, GitAPIException {
            Path guideToFetch = sourceRepoDir.resolve("_guides/" + FETCHED_GUIDE_NAME + ".adoc");
            Path adocToIgnore = sourceRepoDir.resolve("_guides/_attributes.adoc");
            try (Git git = Git.init().setDirectory(sourceRepoDir.toFile()).call()) {
                cleanGitUserConfig();

                PathUtils.createParentDirectories(guideToFetch);
                Files.writeString(guideToFetch, "initial");
                PathUtils.createParentDirectories(adocToIgnore);
                Files.writeString(adocToIgnore, "ignored");
                git.add().addFilepattern(".").call();
                git.commit().setMessage("First commit").call();

                Files.writeString(guideToFetch, FETCHED_GUIDE_CONTENT);
                git.add().addFilepattern(".").call();
                git.commit().setMessage("Second commit").call();
            }
        }
    }

    private static final String FETCHED_GUIDE_NAME = "foo";
    private static final String FETCHED_GUIDE_CONTENT = """
            = Some title
            :irrelevant: foo
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

    private static Consumer<Guide> isGuide(String relativePath, String title, String summary, String keywords,
            Set<String> topics, Set<String> extensions, String content) {
        return guide -> {
            SoftAssertions.assertSoftly(softly -> {
                softly.assertThat(guide).extracting("relativePath").isEqualTo(relativePath);
                softly.assertThat(guide).extracting("title").isEqualTo(title);
                softly.assertThat(guide).extracting("summary").isEqualTo(summary);
                softly.assertThat(guide).extracting("keywords").isEqualTo(keywords);
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

    private static void cleanGitUserConfig() {
        try {
            SystemReader.getInstance().getUserConfig().clear();
        } catch (Exception e) {
            LOG.warn("Unable to clear the Git user config");
        }
    }
}
