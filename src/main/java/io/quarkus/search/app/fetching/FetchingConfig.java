package io.quarkus.search.app.fetching;

import java.net.URI;

import io.smallrye.config.ConfigMapping;

@ConfigMapping(prefix = "fetching")
interface FetchingConfig {
    Source quarkusio();

    interface Source {
        Method method();

        URI uri();

        enum Method {
            GIT,
            LOCAL;
        }
    }
}
