package io.quarkus.search.app.quarkiverseio;

import java.io.Closeable;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import jakarta.ws.rs.core.UriBuilder;

import io.quarkus.search.app.entity.Guide;
import io.quarkus.search.app.hibernate.InputProvider;
import io.quarkus.search.app.indexing.FailureCollector;
import io.quarkus.search.app.indexing.IndexableGuides;
import io.quarkus.search.app.util.CloseableDirectory;

import io.quarkus.logging.Log;

import org.hibernate.search.util.common.impl.Closer;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class QuarkiverseIO implements IndexableGuides, Closeable {

    public static final String QUARKIVERSE_ORIGIN = "quarkiverse-hub";

    private final URI quarkiverseDocsIndex;
    private final FailureCollector failureCollector;

    private final List<Guide> quarkiverseGuides = new ArrayList<>();
    private final boolean enabled;
    private final CloseableDirectory guideHtmls;

    public QuarkiverseIO(QuarkiverseIOConfig config, FailureCollector failureCollector) {
        this.quarkiverseDocsIndex = config.webUri();
        this.enabled = config.enabled();
        this.failureCollector = failureCollector;
        try {
            guideHtmls = CloseableDirectory.temp("quarkiverse_htmls_");
        } catch (IOException e) {
            throw new IllegalStateException("Failed to fetch quarkiverse guides: %s".formatted(e.getMessage()), e);
        }
    }

    public void parseGuides() {
        Document index = null;
        try {
            index = Jsoup.connect(quarkiverseDocsIndex.toString()).get();
        } catch (IOException e) {
            failureCollector.critical(FailureCollector.Stage.PARSING, "Unable to fetch the Quarkiverse Docs index page.", e);
            // no point in doing anything else here:
            return;
        }

        // find links to quarkiverse extension docs:
        Elements quarkiverseGuideIndexLinks = index.select("ul.components li.component a.title");

        for (Element quarkiverseGuideIndexLink : quarkiverseGuideIndexLinks) {
            Guide guide = new Guide();
            String topLevelTitle = quarkiverseGuideIndexLink.text();
            guide.title.set(topLevelTitle);

            Document extensionIndex = null;
            try {
                extensionIndex = readGuide(guide, quarkiverseGuideIndexLink.absUrl("href"), Optional.empty());
            } catch (URISyntaxException | IOException e) {
                failureCollector.warning(FailureCollector.Stage.PARSING,
                        "Unable to fetch guide: " + topLevelTitle, e);
                continue;
            }

            quarkiverseGuides.add(guide);

            // find other sub-pages on the left side
            Map<URI, String> indexLinks = new HashMap<>();
            Elements extensionSubGuides = extensionIndex.select("nav.nav-menu .nav-item a");
            for (Element element : extensionSubGuides) {
                String href = element.absUrl("href");
                URI uri = UriBuilder.fromUri(href).replaceQuery(null).fragment(null).build();
                indexLinks.computeIfAbsent(uri, u -> element.text());
            }

            for (Map.Entry<URI, String> entry : indexLinks.entrySet()) {
                Guide sub = new Guide();
                sub.title.set(entry.getValue());
                try {
                    readGuide(sub, entry.getKey().toString(), Optional.of(topLevelTitle));
                } catch (URISyntaxException | IOException e) {
                    failureCollector.warning(FailureCollector.Stage.PARSING,
                            "Unable to fetch guide: " + topLevelTitle, e);
                    continue;
                }
                quarkiverseGuides.add(sub);
            }
        }
    }

    private Document readGuide(Guide guide, String link, Optional<String> titlePrefix) throws URISyntaxException, IOException {
        guide.url = new URI(link);
        guide.type = "reference";
        guide.origin = QUARKIVERSE_ORIGIN;

        Document extensionIndex = Jsoup.connect(link).get();
        Elements content = extensionIndex.select("div.content");

        String title = content.select("h1.page").text();
        if (!title.isBlank()) {
            String actualTitle = titlePrefix.map(prefix -> "%s: %s".formatted(prefix, title)).orElse(title);
            guide.title.set(actualTitle);
        }
        guide.summary.set(content.select("div#preamble").text());
        guide.htmlFullContentProvider.set(new FileInputProvider(link, dumpHtmlToFile(content.html())));

        Log.debugf("Parsed guide: %s", guide.url);
        return extensionIndex;
    }

    private Path dumpHtmlToFile(String html) throws IOException {
        Path path = guideHtmls.path().resolve(UUID.randomUUID().toString());
        try (FileOutputStream fos = new FileOutputStream(path.toFile())) {
            fos.write(html.getBytes(StandardCharsets.UTF_8));
        }
        return path;
    }

    public Stream<Guide> guides() {
        if (enabled) {
            parseGuides();
        }
        return quarkiverseGuides.stream();
    }

    @Override
    public void close() throws IOException {
        try (var closer = new Closer<IOException>()) {
            closer.push(CloseableDirectory::close, guideHtmls);
            closer.push(List::clear, quarkiverseGuides);
        }
    }

    private record FileInputProvider(String link, Path content) implements InputProvider {

        @Override
        public InputStream open() throws IOException {
            return new FileInputStream(content.toFile());
        }
    }
}
