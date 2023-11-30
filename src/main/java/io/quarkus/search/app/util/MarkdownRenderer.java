package io.quarkus.search.app.util;

import java.util.Set;

import org.commonmark.node.Node;
import org.commonmark.node.Paragraph;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.NodeRenderer;
import org.commonmark.renderer.Renderer;
import org.commonmark.renderer.html.HtmlNodeRendererContext;
import org.commonmark.renderer.html.HtmlRenderer;

public final class MarkdownRenderer {

    private MarkdownRenderer() {
    }

    private static final Renderer RENDERER = HtmlRenderer.builder().nodeRendererFactory(
            ParagraphToBrNodeRenderer::new).build();
    private static final Parser MARKDOWN_PARSER = Parser.builder().build();

    public static String renderMarkdown(String markdown) {
        if (markdown == null) {
            return null;
        }
        return RENDERER.render(MARKDOWN_PARSER.parse(markdown)).trim();
    }

    private static class ParagraphToBrNodeRenderer implements NodeRenderer {
        private final HtmlNodeRendererContext context;

        public ParagraphToBrNodeRenderer(HtmlNodeRendererContext context) {
            this.context = context;
        }

        @Override
        public Set<Class<? extends Node>> getNodeTypes() {
            return Set.of(Paragraph.class);
        }

        @Override
        public void render(Node paragraph) {
            Node node = paragraph.getFirstChild();
            while (node != null) {
                Node next = node.getNext();
                context.render(node);
                node = next;
            }
            if (paragraph.getNext() != null) {
                context.getWriter().tag("br/");
            }

        }
    }
}
