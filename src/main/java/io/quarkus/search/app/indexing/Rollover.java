package io.quarkus.search.app.indexing;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Stream;

import io.quarkus.logging.Log;

import org.hibernate.search.backend.elasticsearch.ElasticsearchBackend;
import org.hibernate.search.backend.elasticsearch.ElasticsearchExtension;
import org.hibernate.search.backend.elasticsearch.index.ElasticsearchIndexManager;
import org.hibernate.search.backend.elasticsearch.metamodel.ElasticsearchIndexDescriptor;
import org.hibernate.search.mapper.orm.entity.SearchIndexedEntity;
import org.hibernate.search.mapper.orm.mapping.SearchMapping;

import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.RestClient;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Implementation of a rollover,
 * i.e. a three-step process that creates new indexes and redirects write aliases to those new indexes,
 * then let the caller run indexing,
 * then commits or rolls back by atomically pointing aliases to the new (respectively old) index
 * and removing the old (respectively new) index.
 */
public class Rollover implements Closeable {

    /**
     * Starts a rollover.
     *
     * @param searchMapping The Hibernate Search mapping.
     * @return A closeable object allowing to commit a rollover,
     *         and which on close will do nothing if committed, or will roll back the rollover otherwise.
     */
    public static Rollover start(SearchMapping searchMapping) {
        Log.info("Starting index rollover");

        var mappings = new HashMap<String, JsonObject>();
        var settings = new HashMap<String, JsonObject>();
        searchMapping.scope(Object.class).schemaManager().exportExpectedSchema((backendName, indexName, export) -> {
            var createIndexRequestBody = export.extension(ElasticsearchExtension.get()).bodyParts().get(0);
            mappings.put(indexName, createIndexRequestBody.getAsJsonObject("mappings"));
            settings.put(indexName, createIndexRequestBody.getAsJsonObject("settings"));
        });

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

    /**
     * Attempts to recover inconsistent aliases that might have been left off
     * by a JVM killed before a rollover was committed or rolled back.
     *
     * @param searchMapping The Hibernate Search mapping.
     * @return {@code true} if a recovering was attempted, {@code false} if nothing could be done.
     */
    public static boolean recoverInconsistentAliases(SearchMapping searchMapping) {
        var client = client(searchMapping);
        var gson = new Gson();
        try {
            var aliased = aliased(client, gson, searchMapping.allIndexedEntities()
                    .stream().map(e -> e.indexManager().unwrap(ElasticsearchIndexManager.class).descriptor())
                    .toList());
            return recoverInconsistentAliases(client, gson, aliased);
        } catch (RuntimeException | IOException e) {
            throw new IllegalStateException("Failed to recover aliases: " + e.getMessage(), e);
        }
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

        // Includes an empty "aliases" so that the aliases are not copied over to the new index,
        // except for the write alias.
        // We don't want the read alias to start pointing to the new index immediately.
        body.add("aliases", new JsonObject());

        request.setEntity(new StringEntity(gson.toJson(body), ContentType.APPLICATION_JSON));
        var response = client.performRequest(request);
        try (var input = response.getEntity().getContent()) {
            var responseBody = gson.fromJson(new InputStreamReader(input, StandardCharsets.UTF_8), JsonObject.class);
            return new IndexRolloverResult(index, responseBody.get("old_index").getAsString(),
                    responseBody.get("new_index").getAsString());
        }
    }

    private record IndexRolloverResult(ElasticsearchIndexDescriptor index, String oldIndex, String newIndex) {
    }

    static Collection<GetAliasedResult> aliased(RestClient client, Gson gson,
            List<ElasticsearchIndexDescriptor> indexes)
            throws IOException {
        var request = new Request("GET", "/_aliases");

        Map<String, GetAliasedResult> resultsByReadAlias = new HashMap<>();
        Map<String, GetAliasedResult> resultsByWriteAlias = new HashMap<>();
        for (ElasticsearchIndexDescriptor index : indexes) {
            var result = new GetAliasedResult(index);
            resultsByReadAlias.put(index.readName(), result);
            resultsByWriteAlias.put(index.writeName(), result);
        }

        var response = client.performRequest(request);
        try (var input = response.getEntity().getContent()) {
            var responseBody = gson.fromJson(new InputStreamReader(input, StandardCharsets.UTF_8), JsonObject.class);
            for (var indexEntry : responseBody.asMap().entrySet()) {
                String indexName = indexEntry.getKey();
                var aliases = indexEntry.getValue().getAsJsonObject().getAsJsonObject("aliases");
                for (var aliasEntry : aliases.asMap().entrySet()) {
                    String alias = aliasEntry.getKey();
                    var aliasMetadata = aliasEntry.getValue().getAsJsonObject();
                    boolean isWriteAlias = aliasMetadata.has("is_write_index")
                            && aliasMetadata.get("is_write_index").getAsBoolean();
                    GetAliasedResult result;
                    if (isWriteAlias) {
                        result = resultsByWriteAlias.get(alias);
                    } else {
                        result = resultsByReadAlias.get(alias);
                    }
                    if (result != null) {
                        result.addActualIndex(indexName, isWriteAlias);
                    }
                }
            }
        }

        return resultsByReadAlias.values();
    }

    static class GetAliasedResult {
        public final ElasticsearchIndexDescriptor index;
        private final Set<String> allAliasedIndexes = new TreeSet<>();
        private final Set<String> writeAliasedIndexes = new TreeSet<>();
        private final Set<String> readAliasedIndexes = new TreeSet<>();

        private GetAliasedResult(ElasticsearchIndexDescriptor index) {
            this.index = index;
        }

        public void addActualIndex(String name, boolean isWrite) {
            allAliasedIndexes.add(name);
            if (isWrite) {
                writeAliasedIndexes.add(name);
            } else {
                readAliasedIndexes.add(name);
            }
        }

        public Set<String> allAliasedIndexes() {
            return Collections.unmodifiableSet(allAliasedIndexes);
        }

        Set<String> writeAliasedIndexes() {
            return Collections.unmodifiableSet(writeAliasedIndexes);
        }

        public Set<String> readAliasedIndexes() {
            return Collections.unmodifiableSet(readAliasedIndexes);
        }
    }

    private static void commitAll(RestClient client, Gson gson, List<IndexRolloverResult> rollovers) {
        Log.info("Committing index rollover");
        try {
            changeAliasesAtomically(client, gson, rollovers, rolloverResult -> {
                JsonObject useNewIndexAsRead = aliasAction("add", Map.of(
                        "index", rolloverResult.newIndex,
                        "alias", rolloverResult.index.readName(),
                        "is_write_index", "false"));
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

    private static boolean recoverInconsistentAliases(RestClient client, Gson gson,
            Collection<GetAliasedResult> aliased) {
        List<GetAliasedResult> inconsistentList = aliased.stream()
                .filter(a -> a.allAliasedIndexes.size() > 1)
                .toList();
        if (inconsistentList.isEmpty()) {
            return false;
        }
        List<String> inconsistentNames = inconsistentList.stream().map(r -> r.index.hibernateSearchName()).toList();
        Log.infof("Recovering index aliases for %s", inconsistentNames);
        try {
            changeAliasesAtomically(client, gson, inconsistentList, inconsistent -> {
                Set<String> extraIndexes = new HashSet<>(inconsistent.allAliasedIndexes);
                String indexToKeep;
                // We keep only the oldest read index,
                // which hopefully should restore a complete index with the ability to search.
                if (!inconsistent.readAliasedIndexes.isEmpty()) {
                    indexToKeep = inconsistent.readAliasedIndexes.iterator().next();
                }
                // Failing that, we keep only one write, and hope for the best.
                else {
                    indexToKeep = inconsistent.allAliasedIndexes.iterator().next();
                }
                extraIndexes.remove(indexToKeep);
                JsonObject restoreIndexToKeepAsWriteIndex = aliasAction("add", Map.of(
                        "index", indexToKeep,
                        "alias", inconsistent.index.writeName(),
                        "is_write_index", "true"));
                JsonObject restoreIndexToKeepAsReadIndex = aliasAction("add", Map.of(
                        "index", indexToKeep,
                        "alias", inconsistent.index.readName(),
                        "is_write_index", "false"));
                return Stream.concat(
                        Stream.of(restoreIndexToKeepAsWriteIndex, restoreIndexToKeepAsReadIndex),
                        extraIndexes.stream()
                                .map(indexName -> aliasAction("remove_index", Map.of("index", indexName))));
            });
        } catch (RuntimeException | IOException e) {
            throw new IllegalStateException("Failed to recover index aliases for " + inconsistentNames + ": " + e.getMessage(),
                    e);
        }
        return true;
    }

    private static <T> void changeAliasesAtomically(RestClient client, Gson gson, List<T> input,
            Function<T, Stream<JsonObject>> actionsFunction) throws IOException {
        var request = new Request("POST", "_aliases");
        JsonObject body = new JsonObject();
        JsonArray actions = new JsonArray();
        body.add("actions", actions);
        input.stream().flatMap(actionsFunction).forEach(actions::add);
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
