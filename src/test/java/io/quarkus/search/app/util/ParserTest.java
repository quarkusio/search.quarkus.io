package io.quarkus.search.app.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ParserTest {
    @Test
    void renderingOfMarkdown() {
        assertThat(MarkdownRenderer.renderMarkdown("some text"))
                .isEqualTo("some text");
        assertThat(MarkdownRenderer.renderMarkdown("some text\n\nmore text"))
                .isEqualTo("some text<br/>more text");
        assertThat(MarkdownRenderer.renderMarkdown(
                "Quarkus DI solution is based on the [Jakarta Contexts and Dependency Injection 4.0](https://jakarta.ee/specifications/cdi/4.0/jakarta-cdi-spec-4.0.html) specification."))
                .isEqualTo(
                        "Quarkus DI solution is based on the <a href=\"https://jakarta.ee/specifications/cdi/4.0/jakarta-cdi-spec-4.0.html\">Jakarta Contexts and Dependency Injection 4.0</a> specification.");
    }

}
