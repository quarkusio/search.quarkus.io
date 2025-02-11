package io.quarkus.search.app.hibernate;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import io.quarkus.search.app.util.CloseableDirectory;

import io.quarkus.logging.Log;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

public record InputProvider(Path content) {

    public InputStream open() throws IOException {
        return new FileInputStream(content.toFile());
    }

    public static InputProvider from(Document document, CloseableDirectory temp, Object context) {
        Element body = document.body();
        // Content div has two grid columns: actual content and TOC. There's not much use of the TOC, we want the content only:
        Element content = body.selectFirst(".guide");
        String writableContent = null;
        if (content != null) {
            // Remove meaningless/duplicate content
            content.select(".toc, .tocwrapper, .relations")
                    .remove();
            writableContent = encode(content);
        } else {
            // we might be looking at a quarkiverse guide; in such case:
            content = body.selectFirst("article.doc");
            if (content != null) {
                // Means we've found a guide content column. hence let's use that to have only real content:
                writableContent = encode(content);
            } else {
                Log.warnf(
                        "Was unable to find the content section of a guide. Using whole document as text. %s Document starts with: %.10000s",
                        context, body.toString());
                writableContent = encode(body);
            }
        }

        try {
            Path path = Files.writeString(Files.createTempFile(temp.path(), "preprocessed_", ""), writableContent,
                    StandardCharsets.UTF_8);
            return new InputProvider(path);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to store a preprocessed content for '" + content + "': " + e.getMessage(),
                    e);
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
