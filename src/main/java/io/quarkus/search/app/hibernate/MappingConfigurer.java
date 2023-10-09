package io.quarkus.search.app.hibernate;

import java.nio.file.Path;

import io.quarkus.hibernate.search.orm.elasticsearch.SearchExtension;
import jakarta.enterprise.context.Dependent;

import org.hibernate.search.mapper.orm.mapping.HibernateOrmMappingConfigurationContext;
import org.hibernate.search.mapper.orm.mapping.HibernateOrmSearchMappingConfigurer;

@Dependent
@SearchExtension
public class MappingConfigurer implements HibernateOrmSearchMappingConfigurer {
    @Override
    public void configure(HibernateOrmMappingConfigurationContext context) {
        context.bridges().exactType(PathWrapper.class)
                .valueBridge(new PathBridge());
    }
}
