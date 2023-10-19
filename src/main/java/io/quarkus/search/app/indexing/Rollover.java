package io.quarkus.search.app.indexing;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

import org.hibernate.search.backend.elasticsearch.ElasticsearchBackend;
import org.hibernate.search.backend.elasticsearch.ElasticsearchExtension;
import org.hibernate.search.backend.elasticsearch.index.ElasticsearchIndexManager;
import org.hibernate.search.backend.elasticsearch.metamodel.ElasticsearchIndexDescriptor;
import org.hibernate.search.engine.common.schema.management.SchemaExport;
import org.hibernate.search.mapper.orm.entity.SearchIndexedEntity;
import org.hibernate.search.mapper.orm.mapping.SearchMapping;
import org.hibernate.search.mapper.pojo.schema.management.SearchSchemaCollector;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.quarkus.logging.Log;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.RestClient;

/**
 * Implementation of a rollover,
 * i.e. a three-step process that creates new indexes and redirects write aliases to those new indexes,
 * then let the caller run indexing,
 * then commits or rolls back by atomically pointing aliases to the new (respectively old) index
 * and removing the old (respectively new) index.
 */
public class Rollover implements Closeable {

    public static Rollover start(SearchMapping searchMapping) {
        Log.info("Starting index rollover");

        var mappings = new HashMap<String, JsonObject>();
        var settings = new HashMap<String, JsonObject>();
        var schemaCollector = new SearchSchemaCollector() {
            @Override
            public void indexSchema(Optional<String> backendName, String indexName, SchemaExport export) {
                var createIndexRequestBody = export.extension(ElasticsearchExtension.get()).bodyParts().get(0);
                mappings.put(indexName, createIndexRequestBody.getAsJsonObject("mappings"));
                settings.put(indexName, createIndexRequestBody.getAsJsonObject("settings"));
            }
        };
        searchMapping.scope(Object.class).schemaManager().exportExpectedSchema(schemaCollector);

        var client = client(searchMapping);
        var gson = new Gson();
        List<IndexRolloverResult> successfulRollovers = new ArrayList<>();
        try {
            for (SearchIndexedEntity<?> entity : searchMapping.allIndexedEntities()) {
                var index = entity.indexManager().unwrap(ElasticsearchIndexManager.class).descriptor();
                successfulRollovers.add(rollover(client, gson, index,
                        mappings.get(index.hibernateSearchName()), settings.get(index.hibernateSearchName())));
            }
        } catch (RuntimeException | IOException e) {
            try {
                rollbackAll(client, gson, successfulRollovers);
            } catch (RuntimeException e2) {
                e.addSuppressed(e2);
            }
            throw new IllegalStateException("Failed to start rollover: " + e.getMessage(), e);
        }
        return new Rollover(client, gson, successfulRollovers);
    }

    private record IndexRolloverResult(ElasticsearchIndexDescriptor index, String oldIndex, String newIndex) {
    }

    private static RestClient client(SearchMapping searchMapping) {
        return searchMapping.backend().unwrap(ElasticsearchBackend.class).client(RestClient.class);
    }

    private final RestClient client;
    private final Gson gson;
    private final List<IndexRolloverResult> indexRolloverResults;
    private boolean done;

    private Rollover(RestClient client, Gson gson, List<IndexRolloverResult> indexRolloverResults) {
        this.client = client;
        this.gson = gson;
        this.indexRolloverResults = indexRolloverResults;
    }

    @Override
    public void close() {
        if (!done) {
            rollback();
        }
    }

    public void commit() {
        commitAll(client, gson, indexRolloverResults);
        done = true;
    }

    public void rollback() {
        rollbackAll(client, gson, indexRolloverResults);
        done = true;
    }

    private static IndexRolloverResult rollover(RestClient client, Gson gson, ElasticsearchIndexDescriptor index,
            JsonObject mapping, JsonObject settings)
            throws IOException {
        var request = new Request("POST", "/" + index.writeName() + "/_rollover");
        JsonObject body = new JsonObject();
        body.add("mappings", mapping);
        body.add("settings", settings);
        request.setEntity(new StringEntity(gson.toJson(body), ContentType.APPLICATION_JSON));
        var response = client.performRequest(request);
        try (var input = response.getEntity().getContent()) {
            var responseBody = gson.fromJson(new InputStreamReader(input, StandardCharsets.UTF_8), JsonObject.class);
            return new IndexRolloverResult(index, responseBody.get("old_index").getAsString(),
                    responseBody.get("new_index").getAsString());
        }
    }

    private static void commitAll(RestClient client, Gson gson, List<IndexRolloverResult> rollovers) {
        Log.info("Committing index rollover");
        try {
            changeAliasesAtomically(client, gson, rollovers, rolloverResult -> {
                JsonObject useNewIndexAsRead = aliasAction("add", Map.of(
                        "index", rolloverResult.newIndex,
                        "alias", rolloverResult.index.readName()));
                JsonObject removeOldIndex = aliasAction("remove_index", Map.of(
                        "index", rolloverResult.oldIndex));
                return Stream.of(useNewIndexAsRead, removeOldIndex);
            });
        } catch (RuntimeException | IOException e) {
            throw new IllegalStateException("Failed to commit rollover: " + e.getMessage(), e);
        }
    }

    private static void rollbackAll(RestClient client, Gson gson, List<IndexRolloverResult> rollovers) {
        Log.info("Rolling back index rollover");
        try {
            changeAliasesAtomically(client, gson, rollovers, rolloverResult -> {
                JsonObject restoreOldIndexAsWriteIndex = aliasAction("add", Map.of(
                        "index", rolloverResult.oldIndex,
                        "alias", rolloverResult.index.writeName(),
                        "is_write_index", "true"));
                JsonObject removeNewIndex = aliasAction("remove_index", Map.of(
                        "index", rolloverResult.newIndex));
                return Stream.of(restoreOldIndexAsWriteIndex, removeNewIndex);
            });
        } catch (RuntimeException | IOException e) {
            throw new IllegalStateException("Failed to rollback rollover: " + e.getMessage(), e);
        }
    }

    private static void changeAliasesAtomically(RestClient client, Gson gson, List<IndexRolloverResult> rollovers,
            Function<IndexRolloverResult, Stream<JsonObject>> actionsFunction) throws IOException {
        var request = new Request("POST", "_aliases");
        JsonObject body = new JsonObject();
        JsonArray actions = new JsonArray();
        body.add("actions", actions);
        rollovers.stream().flatMap(actionsFunction).forEach(actions::add);
        request.setEntity(new StringEntity(gson.toJson(body), ContentType.APPLICATION_JSON));
        client.performRequest(request);
    }

    private static JsonObject aliasAction(String actionName, Map<String, String> parameters) {
        JsonObject outer = new JsonObject();
        JsonObject inner = new JsonObject();
        outer.add(actionName, inner);
        parameters.forEach(inner::addProperty);
        return outer;
    }

}
