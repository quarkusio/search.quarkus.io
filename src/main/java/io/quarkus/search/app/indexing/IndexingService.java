package io.quarkus.search.app.indexing;

import java.io.IOException;
import java.util.Iterator;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import org.hibernate.Session;
import org.hibernate.search.mapper.orm.mapping.SearchMapping;
import org.hibernate.search.util.common.impl.Closer;

import io.quarkus.logging.Log;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.search.app.fetching.FetchingService;
import io.quarkus.search.app.fetching.QuarkusIO;
import io.quarkus.vertx.http.ManagementInterface;

@ApplicationScoped
public class IndexingService {
    private static final int INDEXING_BATCH_SIZE = 50;

    @Inject
    SearchMapping searchMapping;

    @Inject
    Session session;

    @Inject
    FetchingService fetchingService;

    void registerManagementRoutes(@Observes ManagementInterface mi) {
        mi.router().get("/reindex")
                .blockingHandler(rc -> {
                    reindex();
                    rc.end("Success");
                });
    }

    void indexOnStartup(@Observes StartupEvent ev,
            @ConfigProperty(name = "indexing.on-startup", defaultValue = "true") boolean index) {
        if (index) {
            reindex();
        }
    }

    @ActivateRequestContext
    protected void reindex() {
        clearIndexes();
        indexAll();
    }

    private void clearIndexes() {
        try {
            Log.info("Clearing all indexes");
            searchMapping.scope(Object.class).schemaManager().dropAndCreate();
        } catch (RuntimeException e) {
            throw new IllegalStateException("Failed to clear all index data: " + e.getMessage(), e);
        }
    }

    private void indexAll() {
        Log.info("Indexing...");
        try (Closer<IOException> closer = new Closer<>()) {
            // We don't use the database for searching, so let's make sure to clear it at the end.
            closer.push(IndexingService::clearDatabaseWithoutIndexes, this);

            try (QuarkusIO quarkusIO = fetchingService.fetchQuarkusIo()) {
                indexQuarkusIo(quarkusIO);
            }
            Log.info("Refreshing indexes...");
            searchMapping.scope(Object.class).workspace().refresh();
            Log.info("Indexing success");
        } catch (Exception e) {
            throw new IllegalStateException("Failed to index data: " + e.getMessage(), e);
        }
    }

    private void indexQuarkusIo(QuarkusIO quarkusIO) throws IOException {
        Log.info("Indexing quarkus.io...");
        try (var guideStream = quarkusIO.guides()) {
            batchIndex(guideStream.iterator());
        }
    }

    private <T> void batchIndex(Iterator<T> docIterator) {
        QuarkusTransaction.begin();
        Throwable failure = null;
        int i = 0;
        try {
            while (docIterator.hasNext()) {
                T doc = docIterator.next();
                session.persist(doc);

                ++i;
                if (i % INDEXING_BATCH_SIZE == 0) {
                    QuarkusTransaction.commit();
                    Log.infof("Indexed %d documents...", i);
                    QuarkusTransaction.begin();
                }
            }
        } catch (Throwable t) {
            failure = t;
        } finally {
            if (failure == null) {
                QuarkusTransaction.commit();
                Log.infof("Indexed %d documents.", i);
            } else {
                try {
                    QuarkusTransaction.rollback();
                } catch (Throwable t) {
                    failure.addSuppressed(t);
                }
            }
        }
    }

    private void clearDatabaseWithoutIndexes() {
        try {
            session.getSessionFactory().getSchemaManager().truncateMappedObjects();
        } catch (RuntimeException e) {
            throw new RuntimeException("Failed to clear the database after indexing: " + e.getMessage(), e);
        }
    }

}
