package io.quarkus.search.app.fetching;

import static io.quarkus.search.app.util.UncheckedIOFunction.uncheckedIO;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import jakarta.inject.Inject;

import io.quarkus.search.app.entity.Guide;
import io.quarkus.search.app.entity.I18nData;
import io.quarkus.search.app.entity.Language;
import io.quarkus.search.app.hibernate.InputProvider;
import io.quarkus.search.app.indexing.FailureCollector;
import io.quarkus.search.app.quarkusio.QuarkusIO;
import io.quarkus.search.app.testsupport.GitTestUtils;
import io.quarkus.search.app.util.CloseableDirectory;

import io.quarkus.test.component.QuarkusComponentTestExtension;

import org.hibernate.search.util.common.impl.Closer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
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
    static EnumMap<Language, CloseableDirectory> localizedDirectories;
    static {
        try {
            tmpDir = CloseableDirectory.temp("fetching-service-test");
            localizedDirectories = new EnumMap<>(Language.class);
            for (Language lang : Language.values()) {
                localizedDirectories.put(lang, CloseableDirectory.temp("fetching-service-test-" + lang.code));
            }
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
                .setInitialBranch(QuarkusIO.MAIN_BRANCHES.pages()).call()) {
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
                    .setName(QuarkusIO.MAIN_BRANCHES.sources())
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

        for (Map.Entry<Language, CloseableDirectory> entry : localizedDirectories.entrySet()) {
            Language language = entry.getKey();
            Path localizedSourceRepoPath = entry.getValue().path();
            Path localizedMetadata1ToFetch = localizedSourceRepoPath
                    .resolve("l10n/po/" + language.locale + "/_data/versioned/latest/index/quarkus.yaml.po");
            Path localizedMetadata2ToFetch = localizedSourceRepoPath
                    .resolve("l10n/po/" + language.locale + "/_data/guides-2-7.yaml.po");
            Path localizedGuide1HtmlToFetch = localizedSourceRepoPath.resolve("docs/guides/" + FETCHED_GUIDE_1_NAME + ".html");
            Path localizedGuide2HtmlToFetch = localizedSourceRepoPath
                    .resolve("docs/version/2.7/guides/" + FETCHED_GUIDE_2_NAME + ".html");
            try (Git git = Git.init().setDirectory(localizedSourceRepoPath.toFile())
                    .setInitialBranch(QuarkusIO.LOCALIZED_BRANCHES.pages()).call()) {
                GitTestUtils.cleanGitUserConfig();

                RevCommit initialCommit = git.commit().setMessage("Initial commit")
                        .setAllowEmpty(true)
                        .call();

                PathUtils.createParentDirectories(localizedGuide1HtmlToFetch);
                Files.writeString(localizedGuide1HtmlToFetch, "initial");
                git.add().addFilepattern(".").call();
                git.commit().setMessage("Pages first commit").call();

                Files.writeString(localizedGuide1HtmlToFetch, JA_FETCHED_GUIDE_1_CONTENT_HTML);
                PathUtils.createParentDirectories(localizedGuide2HtmlToFetch);
                Files.writeString(localizedGuide2HtmlToFetch, JA_FETCHED_GUIDE_2_CONTENT_HTML);
                git.add().addFilepattern(".").call();
                git.commit().setMessage("Pages second commit").call();

                git.checkout()
                        .setName(QuarkusIO.LOCALIZED_BRANCHES.sources())
                        .setCreateBranch(true)
                        .setStartPoint(initialCommit)
                        .call();

                PathUtils.createParentDirectories(localizedMetadata1ToFetch);
                Files.writeString(localizedMetadata1ToFetch, JA_PO_METADATA_YAML);
                PathUtils.createParentDirectories(localizedMetadata2ToFetch);
                Files.writeString(localizedMetadata2ToFetch, JA_PO_METADATA_LEGACY_YAML);
                git.add().addFilepattern(".").call();
                git.commit().setMessage("Source first commit").call();
            }
        }
    }

    private void updateMainRepository() throws IOException, GitAPIException {
        Path sourceRepoPath = tmpDir.path();
        Path metadata1ToFetch = sourceRepoPath.resolve("_data/versioned/latest/index/quarkus.yaml");
        Path guide1HtmlToFetch = sourceRepoPath.resolve("guides/" + FETCHED_GUIDE_1_NAME + ".html");

        try (Git git = Git.open(sourceRepoPath.toFile())) {
            GitTestUtils.cleanGitUserConfig();

            git.checkout().setName(QuarkusIO.MAIN_BRANCHES.pages()).call();
            Files.writeString(guide1HtmlToFetch, FETCHED_GUIDE_1_CONTENT_HTML_UPDATED);
            git.add().addFilepattern(".").call();
            git.commit().setMessage("Pages updated commit").call();

            git.checkout().setName(QuarkusIO.MAIN_BRANCHES.sources()).call();

            Files.writeString(metadata1ToFetch, METADATA_YAML_UPDATED);
            git.add().addFilepattern(".").call();
            git.commit().setMessage("Source updated commit").call();
        }
    }

    @AfterAll
    static void deleteTmpDir() throws IOException {
        try (Closer<IOException> closer = new Closer<>()) {
            closer.push(CloseableDirectory::close, tmpDir);
            closer.pushAll(CloseableDirectory::close, localizedDirectories.values());
        }
    }

    @RegisterExtension
    static final QuarkusComponentTestExtension extension = QuarkusComponentTestExtension.builder()
            .configProperty("fetching.timeout", "PT30s")
            .configProperty("quarkusio.git-uri", tmpDir.path().toUri().toString())
            .configProperty("quarkusio.localized.es.git-uri",
                    localizedDirectories.get(Language.SPANISH).path().toUri().toString())
            .configProperty("quarkusio.localized.pt.git-uri",
                    localizedDirectories.get(Language.PORTUGUESE).path().toUri().toString())
            .configProperty("quarkusio.localized.cn.git-uri",
                    localizedDirectories.get(Language.CHINESE).path().toUri().toString())
            .configProperty("quarkusio.localized.ja.git-uri",
                    localizedDirectories.get(Language.JAPANESE).path().toUri().toString())
            // Disable indexing, as it's not necessary here and makes the test slower.
            .configProperty("indexing.on-startup.when", "never")
            .configProperty("quarkus.elasticsearch.devservices.enabled", "false")
            .build();

    @Inject
    FetchingService service;

    @Test
    void fetchQuarkusIo() throws Exception {
        try (QuarkusIO quarkusIO = service.fetchQuarkusIo(new FailureCollector())) {
            try (var guides = quarkusIO.guides()) {
                assertThat(guides)
                        .hasSize(10)
                        .allSatisfy(guide -> assertThat(guide).satisfies(switch (guide.url.toString()) {
                            case "https://quarkus.io/guides/" + FETCHED_GUIDE_1_NAME -> isGuide(
                                    Language.ENGLISH,
                                    "Some title",
                                    "This is a summary",
                                    "keyword1 keyword2",
                                    Set.of("category1", "category2"),
                                    Set.of("topic1", "topic2"),
                                    Set.of("io.quarkus:extension1", "io.quarkus:extension2"),
                                    FETCHED_GUIDE_1_CONTENT_HTML);
                            case "https://cn.quarkus.io/guides/" + FETCHED_GUIDE_1_NAME -> isGuide(
                                    Language.CHINESE,
                                    "何かのタイトル",
                                    "これは概要です",
                                    "keyword1 keyword2",
                                    Set.of("category1", "category2"),
                                    Set.of("topic1", "topic2"),
                                    Set.of("io.quarkus:extension1", "io.quarkus:extension2"),
                                    JA_FETCHED_GUIDE_1_CONTENT_HTML);
                            case "https://es.quarkus.io/guides/" + FETCHED_GUIDE_1_NAME -> isGuide(
                                    Language.SPANISH,
                                    "何かのタイトル",
                                    "これは概要です",
                                    "keyword1 keyword2",
                                    Set.of("category1", "category2"),
                                    Set.of("topic1", "topic2"),
                                    Set.of("io.quarkus:extension1", "io.quarkus:extension2"),
                                    JA_FETCHED_GUIDE_1_CONTENT_HTML);
                            case "https://ja.quarkus.io/guides/" + FETCHED_GUIDE_1_NAME -> isGuide(
                                    Language.JAPANESE,
                                    "何かのタイトル",
                                    "これは概要です",
                                    "keyword1 keyword2",
                                    Set.of("category1", "category2"),
                                    Set.of("topic1", "topic2"),
                                    Set.of("io.quarkus:extension1", "io.quarkus:extension2"),
                                    JA_FETCHED_GUIDE_1_CONTENT_HTML);
                            case "https://pt.quarkus.io/guides/" + FETCHED_GUIDE_1_NAME -> isGuide(
                                    Language.PORTUGUESE,
                                    "何かのタイトル",
                                    "これは概要です",
                                    "keyword1 keyword2",
                                    Set.of("category1", "category2"),
                                    Set.of("topic1", "topic2"),
                                    Set.of("io.quarkus:extension1", "io.quarkus:extension2"),
                                    JA_FETCHED_GUIDE_1_CONTENT_HTML);
                            case "https://quarkus.io/version/2.7/guides/" + FETCHED_GUIDE_2_NAME -> isGuide(
                                    Language.ENGLISH,
                                    "Some other title",
                                    "This is a different summary.",
                                    null,
                                    Set.of("getting-started"),
                                    Set.of(),
                                    Set.of(),
                                    FETCHED_GUIDE_2_CONTENT_HTML);
                            case "https://cn.quarkus.io/version/2.7/guides/" + FETCHED_GUIDE_2_NAME -> isGuide(
                                    Language.CHINESE,
                                    "Some other title",
                                    // Even though there's a translation available it is "fuzzy", hence we ignore it
                                    // and use the original message:
                                    "This is a different summary.",
                                    null,
                                    Set.of("getting-started"),
                                    Set.of(),
                                    Set.of(),
                                    JA_FETCHED_GUIDE_2_CONTENT_HTML);
                            case "https://es.quarkus.io/version/2.7/guides/" + FETCHED_GUIDE_2_NAME -> isGuide(
                                    Language.SPANISH,
                                    "Some other title",
                                    "This is a different summary.",
                                    null,
                                    Set.of("getting-started"),
                                    Set.of(),
                                    Set.of(),
                                    JA_FETCHED_GUIDE_2_CONTENT_HTML);
                            case "https://ja.quarkus.io/version/2.7/guides/" + FETCHED_GUIDE_2_NAME -> isGuide(
                                    Language.JAPANESE,
                                    "Some other title",
                                    "This is a different summary.",
                                    null,
                                    Set.of("getting-started"),
                                    Set.of(),
                                    Set.of(),
                                    JA_FETCHED_GUIDE_2_CONTENT_HTML);
                            case "https://pt.quarkus.io/version/2.7/guides/" + FETCHED_GUIDE_2_NAME -> isGuide(
                                    Language.PORTUGUESE,
                                    "Some other title",
                                    "This is a different summary.",
                                    null,
                                    Set.of("getting-started"),
                                    Set.of(),
                                    Set.of(),
                                    JA_FETCHED_GUIDE_2_CONTENT_HTML);
                            default -> Assertions.<Consumer<Guide>> fail("Unexpected URL: " + guide.url);
                        }));
            }
        }
        // now let's update some guides and make sure that the content is fetched correctly:
        updateMainRepository();

        // NOTE that after an update we'll have non-translated titles and summaries,
        //   since in this test we've only updated them in the "main" repository,
        //   and as a result there's no translation for them in localized sites.
        //   Content file though is still the one from the localized site!
        //
        try (QuarkusIO quarkusIO = service.fetchQuarkusIo(new FailureCollector())) {
            try (var guides = quarkusIO.guides()) {
                assertThat(guides)
                        .hasSize(10)
                        .allSatisfy(guide -> assertThat(guide).satisfies(switch (guide.url.toString()) {
                            case "https://quarkus.io/guides/" + FETCHED_GUIDE_1_NAME -> isGuide(
                                    Language.ENGLISH,
                                    "Some updated title",
                                    "This is an updated summary",
                                    "keyword1 keyword2",
                                    Set.of("category1", "category2"),
                                    Set.of("topic1", "topic2"),
                                    Set.of("io.quarkus:extension1", "io.quarkus:extension2"),
                                    FETCHED_GUIDE_1_CONTENT_HTML_UPDATED);
                            case "https://cn.quarkus.io/guides/" + FETCHED_GUIDE_1_NAME -> isGuide(
                                    Language.CHINESE,
                                    "Some updated title",
                                    "This is an updated summary",
                                    "keyword1 keyword2",
                                    Set.of("category1", "category2"),
                                    Set.of("topic1", "topic2"),
                                    Set.of("io.quarkus:extension1", "io.quarkus:extension2"),
                                    JA_FETCHED_GUIDE_1_CONTENT_HTML);
                            case "https://es.quarkus.io/guides/" + FETCHED_GUIDE_1_NAME -> isGuide(
                                    Language.SPANISH,
                                    "Some updated title",
                                    "This is an updated summary",
                                    "keyword1 keyword2",
                                    Set.of("category1", "category2"),
                                    Set.of("topic1", "topic2"),
                                    Set.of("io.quarkus:extension1", "io.quarkus:extension2"),
                                    JA_FETCHED_GUIDE_1_CONTENT_HTML);
                            case "https://ja.quarkus.io/guides/" + FETCHED_GUIDE_1_NAME -> isGuide(
                                    Language.JAPANESE,
                                    "Some updated title",
                                    "This is an updated summary",
                                    "keyword1 keyword2",
                                    Set.of("category1", "category2"),
                                    Set.of("topic1", "topic2"),
                                    Set.of("io.quarkus:extension1", "io.quarkus:extension2"),
                                    JA_FETCHED_GUIDE_1_CONTENT_HTML);
                            case "https://pt.quarkus.io/guides/" + FETCHED_GUIDE_1_NAME -> isGuide(
                                    Language.PORTUGUESE,
                                    "Some updated title",
                                    "This is an updated summary",
                                    "keyword1 keyword2",
                                    Set.of("category1", "category2"),
                                    Set.of("topic1", "topic2"),
                                    Set.of("io.quarkus:extension1", "io.quarkus:extension2"),
                                    JA_FETCHED_GUIDE_1_CONTENT_HTML);
                            case "https://quarkus.io/version/2.7/guides/" + FETCHED_GUIDE_2_NAME -> isGuide(
                                    Language.ENGLISH,
                                    "Some other title",
                                    "This is a different summary.",
                                    null,
                                    Set.of("getting-started"),
                                    Set.of(),
                                    Set.of(),
                                    FETCHED_GUIDE_2_CONTENT_HTML);
                            case "https://cn.quarkus.io/version/2.7/guides/" + FETCHED_GUIDE_2_NAME -> isGuide(
                                    Language.CHINESE,
                                    "Some other title",
                                    // Even though there's a translation available it is "fuzzy", hence we ignore it
                                    // and use the original message:
                                    "This is a different summary.",
                                    null,
                                    Set.of("getting-started"),
                                    Set.of(),
                                    Set.of(),
                                    JA_FETCHED_GUIDE_2_CONTENT_HTML);
                            case "https://es.quarkus.io/version/2.7/guides/" + FETCHED_GUIDE_2_NAME -> isGuide(
                                    Language.SPANISH,
                                    "Some other title",
                                    "This is a different summary.",
                                    null,
                                    Set.of("getting-started"),
                                    Set.of(),
                                    Set.of(),
                                    JA_FETCHED_GUIDE_2_CONTENT_HTML);
                            case "https://ja.quarkus.io/version/2.7/guides/" + FETCHED_GUIDE_2_NAME -> isGuide(
                                    Language.JAPANESE,
                                    "Some other title",
                                    "This is a different summary.",
                                    null,
                                    Set.of("getting-started"),
                                    Set.of(),
                                    Set.of(),
                                    JA_FETCHED_GUIDE_2_CONTENT_HTML);
                            case "https://pt.quarkus.io/version/2.7/guides/" + FETCHED_GUIDE_2_NAME -> isGuide(
                                    Language.PORTUGUESE,
                                    "Some other title",
                                    "This is a different summary.",
                                    null,
                                    Set.of("getting-started"),
                                    Set.of(),
                                    Set.of(),
                                    JA_FETCHED_GUIDE_2_CONTENT_HTML);
                            default -> Assertions.<Consumer<Guide>> fail("Unexpected URL: " + guide.url);
                        }));
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

    private static final String METADATA_YAML_UPDATED = """
            # Generated file. Do not edit
            ---
            types:
              reference:
              - title: Some updated title
                filename: foo.adoc
                summary: This is an updated summary
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

    private static final String FETCHED_GUIDE_1_CONTENT_HTML_UPDATED = """
            <html>
            <head></head>
            <body>
            <h1>Some title</h1>
            <p>This is the updated guide body
            <h2>Some updated subsection</h2>
            This is an updated subsection
            <h2>Some other updated subsection</h2>
            This is another updated subsection
            """;
    private static final String FETCHED_GUIDE_2_NAME = "bar";
    private static final String FETCHED_GUIDE_2_CONTENT_HTML = """
            <html>
            <head></head>
            <body>
            <h1>Some other title</h1>
            <p>This is the other guide body
            """;
    private static final String JA_PO_METADATA_YAML = """
            msgid ""
            msgstr ""
            "POT-Creation-Date: 2023-12-04 02:36+0000\\n"
            "Language: ja_JP\\n"
            "MIME-Version: 1.0\\n"
            "Content-Type: text/plain; charset=UTF-8\\n"
            "Content-Transfer-Encoding: 8bit\\n"
            "X-Generator: doc-l10n-kit\\n"

            msgid "Some title"
            msgstr "何かのタイトル"

            msgid "This is a summary"
            msgstr "これは概要です"
            """;

    private static final String JA_PO_METADATA_LEGACY_YAML = """
            msgid ""
            msgstr ""
            "POT-Creation-Date: 2023-12-04 02:36+0000\\n"
            "Language: ja_JP\\n"
            "MIME-Version: 1.0\\n"
            "Content-Type: text/plain; charset=UTF-8\\n"
            "Content-Transfer-Encoding: 8bit\\n"
            "X-Generator: doc-l10n-kit\\n"

            #. type: Hash Value: types tutorial title
            #: upstream/_data/versioned/latest/index/quarkus.yaml:0
            #, fuzzy, no-wrap
            msgid "This is a different summary."
            msgstr "別のまとめです"
            """;
    private static final String JA_FETCHED_GUIDE_1_CONTENT_HTML = """
            <html>
            <head></head>
            <body>
            <h1>何かのタイトル</h1>
            <p>こちらがガイド本体です
            <h2>一部のサブセクション</h2>
            これはサブセクションです
            <h2>他のサブセクション</h2>
            これは別のサブセクションです
            """;
    private static final String JA_FETCHED_GUIDE_2_CONTENT_HTML = """
            <html>
            <head></head>
            <body>
            <h1>他のタイトル</h1>
            <p>こちらはもう一方のガイド本体です
            """;

    private static Consumer<Guide> isGuide(Language language, String title, String summary, String keywords,
            Set<String> categories, Set<String> topics, Set<String> extensions, String content) {
        return guide -> {
            SoftAssertions.assertSoftly(softly -> {
                softly.assertThat(guide).extracting("language").isEqualTo(language);
                softly.assertThat(guide).extracting(g -> g.title.asMap(), InstanceOfAssertFactories.MAP)
                        .containsExactlyInAnyOrderEntriesOf(Map.of(language, title));
                softly.assertThat(guide).extracting(g -> g.summary.asMap(), InstanceOfAssertFactories.MAP)
                        .containsExactlyInAnyOrderEntriesOf(Map.of(language, summary));
                softly.assertThat(guide).extracting(g -> g.keywords.asMap(), InstanceOfAssertFactories.MAP)
                        .containsExactlyInAnyOrderEntriesOf(keywords == null ? Map.of() : Map.of(language, keywords));
                softly.assertThat(guide).extracting("categories", InstanceOfAssertFactories.COLLECTION)
                        .containsExactlyInAnyOrderElementsOf(categories);
                softly.assertThat(guide)
                        .extracting(g -> g.topics.stream().map(I18nData::asMap).collect(Collectors.toList()),
                                InstanceOfAssertFactories.COLLECTION)
                        .containsExactlyInAnyOrderElementsOf(topics.stream().map(t -> Map.of(language, t)).toList());
                softly.assertThat(guide).extracting("extensions", InstanceOfAssertFactories.COLLECTION)
                        .containsExactlyInAnyOrderElementsOf(extensions);
                softly.assertThat(guide)
                        .extracting(g -> g.htmlFullContentProvider.asMap(), InstanceOfAssertFactories.MAP)
                        .containsOnlyKeys(language)
                        .allSatisfy((ignored, inputProvider) -> assertThat(inputProvider)
                                .asInstanceOf(InstanceOfAssertFactories.type(InputProvider.class))
                                .extracting(uncheckedIO(InputProvider::open), InstanceOfAssertFactories.INPUT_STREAM)
                                .hasContent(content));
            });
        };
    }
}
