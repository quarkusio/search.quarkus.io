package io.quarkus.search.app.hibernate;

import java.io.IOException;
import java.io.InputStream;

public interface InputProvider {

    InputStream open() throws IOException;

}
