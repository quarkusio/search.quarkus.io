package io.quarkus.search.app.hibernate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.hibernate.search.mapper.pojo.bridge.ValueBridge;
import org.hibernate.search.mapper.pojo.bridge.runtime.ValueBridgeToIndexedValueContext;

import io.quarkus.logging.Log;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;

// TODO It's not reasonable to put the full content of a (potentially large) text file in memory
//  See https://hibernate.atlassian.net/browse/HSEARCH-4975
public class InputProviderHtmlBodyTextBridge implements ValueBridge<InputProvider, String> {
    @Override
    public String toIndexedValue(InputProvider provider, ValueBridgeToIndexedValueContext context) {
        try (var in = provider.open()) {
            Element body = Jsoup.parse(in, StandardCharsets.UTF_8.name(), "/").body();
            // Content div has two grid columns: actual content and TOC. There's not much use of the TOC, we want the content only:
            Element content = body.selectFirst(".content .grid__item");
            if (content != null) {
                // Means we've found a guide content column. hence let's use that to have only real content:
                return content.text();
            } else {
                // we might be looking at a quarkiverse guide; in such case:
                content = body.selectFirst("article.doc");
                if (content != null) {
                    // Means we've found a guide content column. hence let's use that to have only real content:
                    return content.text();
                } else {
                    Log.warnf("Was unable to find the content section of a guide. Using whole document as text. %s", provider);
                    return body.text();
                }
            }
        } catch (RuntimeException | IOException e) {
            throw new IllegalStateException("Failed to read '" + provider + "' for indexing: " + e.getMessage(), e);
        }
    }
}
