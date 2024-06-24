package io.quarkus.search.app.hibernate;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;

import io.quarkus.search.app.entity.Guide;

import org.hibernate.search.mapper.pojo.standalone.loading.LoadingTypeGroup;
import org.hibernate.search.mapper.pojo.standalone.loading.MassEntityLoader;
import org.hibernate.search.mapper.pojo.standalone.loading.MassEntitySink;
import org.hibernate.search.mapper.pojo.standalone.loading.MassIdentifierLoader;
import org.hibernate.search.mapper.pojo.standalone.loading.MassIdentifierSink;
import org.hibernate.search.mapper.pojo.standalone.loading.MassLoadingOptions;
import org.hibernate.search.mapper.pojo.standalone.loading.MassLoadingStrategy;
import org.hibernate.search.mapper.pojo.standalone.loading.binding.EntityLoadingBinder;
import org.hibernate.search.mapper.pojo.standalone.loading.binding.EntityLoadingBindingContext;

@ApplicationScoped
@Named("guideLoadingBinder")
public class GuideLoadingBinder implements EntityLoadingBinder {

    @Override
    public void bind(EntityLoadingBindingContext entityLoadingBindingContext) {
        entityLoadingBindingContext.massLoadingStrategy(Guide.class, new MassLoadingStrategy<Guide, Guide>() {
            @Override
            public MassIdentifierLoader createIdentifierLoader(LoadingTypeGroup<Guide> includedTypes,
                    MassIdentifierSink<Guide> sink, MassLoadingOptions options) {
                QuarkusIOLoadingContext context = options.context(QuarkusIOLoadingContext.class);
                return new MassIdentifierLoader() {
                    @Override
                    public void close() {
                    }

                    @Override
                    public long totalCount() {
                        return context.size();
                    }

                    @Override
                    public void loadNext() throws InterruptedException {
                        List<Guide> batch = context.nextBatch(options.batchSize());
                        if (batch.isEmpty()) {
                            sink.complete();
                        } else {
                            sink.accept(batch);
                        }
                    }
                };
            }

            @Override
            public MassEntityLoader<Guide> createEntityLoader(LoadingTypeGroup<Guide> includedTypes, MassEntitySink<Guide> sink,
                    MassLoadingOptions options) {
                return new MassEntityLoader<Guide>() {
                    @Override
                    public void close() {
                    }

                    @Override
                    public void load(List<Guide> guides) throws InterruptedException {
                        sink.accept(guides);
                    }
                };
            }
        });
    }
}
