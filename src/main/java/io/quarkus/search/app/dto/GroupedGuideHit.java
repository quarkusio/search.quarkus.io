package io.quarkus.search.app.dto;

import java.net.URI;

public record GroupedGuideHit(URI url, String type, String status, String origin, String title, String summary) {
}
