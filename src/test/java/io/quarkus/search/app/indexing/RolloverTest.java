package io.quarkus.search.app.indexing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;

import io.quarkus.search.app.entity.Guide;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;

import org.hibernate.search.backend.elasticsearch.ElasticsearchBackend;
import org.hibernate.search.backend.elasticsearch.index.ElasticsearchIndexManager;
import org.hibernate.search.backend.elasticsearch.metamodel.ElasticsearchIndexDescriptor;
import org.hibernate.search.mapper.orm.entity.SearchIndexedEntity;
import org.hibernate.search.mapper.orm.mapping.SearchMapping;
import org.hibernate.search.mapper.orm.session.SearchSession;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import org.elasticsearch.client.Request;
import org.elasticsearch.client.RestClient;

import com.google.gson.Gson;

@QuarkusTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestProfile(RolloverTest.Profile.class)
class RolloverTest {
    // We mess with the index content, and this profile makes sure this won't affect other tests
    public static class Profile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("indexing.on-startup.when", "never");
        }
    }

    final Gson gson = new Gson();

    @Inject
    SearchMapping searchMapping;
    @Inject
    SearchSession searchSession;
    RestClient client;

    @PostConstruct
    void setClient() {
        client = searchMapping.backend().unwrap(ElasticsearchBackend.class)
                .client(RestClient.class);
    }

    // We test only one index, to keep things simple
    private ElasticsearchIndexDescriptor index(SearchIndexedEntity<?> entity) {
        return entity.indexManager().unwrap(ElasticsearchIndexManager.class).descriptor();
    }

    private Rollover.GetAliasedResult aliased(SearchIndexedEntity<?> entity) {
        try {
            return Rollover.aliased(client, gson, List.of(index(entity))).iterator().next();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private void assertWriteTargetsIndex(int writeIndex) {
        // Search targets the read index, and the read index must be the one we expect
        var aliased = aliased(searchMapping.indexedEntity(Guide.class));
        assertThat(aliased.writeAliasedIndexes())
                .containsExactlyInAnyOrder(String.format("guide-%06d", writeIndex));
    }

    private void assertSearchWorksAndTargetsIndex(int readIndex) {
        // Search must work
        assertThatCode(() -> QuarkusTransaction.requiringNew().call(
                () -> searchSession.search(Guide.class).where(f -> f.matchAll()).fetchTotalHitCount()))
                .doesNotThrowAnyException();

        // Search targets the read index, and the read index must be the one we expect
        var aliased = aliased(searchMapping.indexedEntity(Guide.class));
        assertThat(aliased.readAliasedIndexes())
                .containsExactlyInAnyOrder(String.format("guide-%06d", readIndex));
    }

    @BeforeEach
    void recreateIndexes() {
        Set<String> aliasedIndexes = searchMapping.allIndexedEntities().stream()
                .map(this::aliased)
                .map(Rollover.GetAliasedResult::allAliasedIndexes)
                .flatMap(Set::stream)
                .collect(Collectors.toSet());
        if (!aliasedIndexes.isEmpty()) {
            try {
                client.performRequest(new Request("DELETE", "/" + String.join(",", aliasedIndexes)));
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }
        searchMapping.scope(Object.class).schemaManager().createOrValidate();
    }

    @Test
    void commit() {
        assertSearchWorksAndTargetsIndex(1);
        assertWriteTargetsIndex(1);

        try (Rollover rollover = Rollover.start(searchMapping)) {
            assertSearchWorksAndTargetsIndex(1);
            assertWriteTargetsIndex(2);

            rollover.commit();
            assertSearchWorksAndTargetsIndex(2);
            assertWriteTargetsIndex(2);
        }

        assertSearchWorksAndTargetsIndex(2);
        assertWriteTargetsIndex(2);
    }

    @Test
    void rollback() {
        assertSearchWorksAndTargetsIndex(1);
        assertWriteTargetsIndex(1);

        try (Rollover rollover = Rollover.start(searchMapping)) {
            assertSearchWorksAndTargetsIndex(1);
            assertWriteTargetsIndex(2);
        }

        assertSearchWorksAndTargetsIndex(1);
        assertWriteTargetsIndex(1);
    }

    @Test
    @SuppressWarnings("resource")
    void recover() {
        assertSearchWorksAndTargetsIndex(1);
        assertWriteTargetsIndex(1);

        Rollover.start(searchMapping);
        assertSearchWorksAndTargetsIndex(1);
        assertWriteTargetsIndex(2);

        // Simulate a JVM crash: the rollover does not get closed
        // We restart... and try to recover
        assertThat(Rollover.recoverInconsistentAliases(searchMapping)).isTrue();
        // After recovery and throughout indexing, search must continue to work,
        // and target the index most likely to contain (complete) data.
        assertSearchWorksAndTargetsIndex(1);
        assertWriteTargetsIndex(1);

        // Then we will reindex, triggering another rollover...
        searchMapping.scope(Guide.class).schemaManager().createIfMissing();
        assertSearchWorksAndTargetsIndex(1);
        assertWriteTargetsIndex(1);

        try (Rollover rollover = Rollover.start(searchMapping)) {
            assertSearchWorksAndTargetsIndex(1);
            assertWriteTargetsIndex(2);

            rollover.commit();
            assertSearchWorksAndTargetsIndex(2);
            assertWriteTargetsIndex(2);
        }
        // And all is good in the end!
        assertSearchWorksAndTargetsIndex(2);
        assertWriteTargetsIndex(2);
    }

}
