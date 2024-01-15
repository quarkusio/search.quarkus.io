package io.quarkus.search.app.hibernate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import io.quarkus.logging.Log;

import org.hibernate.search.mapper.pojo.bridge.ValueBridge;
import org.hibernate.search.mapper.pojo.bridge.runtime.ValueBridgeToIndexedValueContext;

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
            Element content = body.selectFirst(".guide");
            if (content != null) {
                // Remove meaningless/duplicate content
                content.select(".toc, .tocwrapper, .relations")
                        .remove();
                return encode(content);
            } else {
                // we might be looking at a quarkiverse guide; in such case:
                content = body.selectFirst("article.doc");
                if (content != null) {
                    // Means we've found a guide content column. hence let's use that to have only real content:
                    return encode(content);
                } else {
                    Log.warnf(
                            "Was unable to find the content section of a guide. Using whole document as text. %s",
                            provider);
                    return encode(body);
                }
            }
        } catch (RuntimeException | IOException e) {
            throw new IllegalStateException("Failed to read '" + provider + "' for indexing: " + e.getMessage(), e);
        }
    }

    /**
     * We want to encode the guide content before indexing to make it safe to return on search results
     * and do not worry about encoding it on each search response.
     */
    private static String encode(Element element) {
        String input = element.text();
        StringBuilder result = new StringBuilder(input.length());

        for (int i = 0; i < input.length(); i++) {
            char ch = input.charAt(i);
            switch (ch) {
                case '"':
                    result.append("&quot;");
                    break;
                case '&':
                    result.append("&amp;");
                    break;
                case '\'':
                    result.append("&#x27;");
                    break;
                case '/':
                    result.append("&#x2F;");
                    break;
                case '<':
                    result.append("&lt;");
                    break;
                case '>':
                    result.append("&gt;");
                    break;
                default:
                    result.append(ch);
            }
        }

        return result.toString();
    }
}
