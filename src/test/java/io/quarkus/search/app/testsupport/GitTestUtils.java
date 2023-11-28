package io.quarkus.search.app.testsupport;

import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.util.SystemReader;

import org.jboss.logging.Logger;

public final class GitTestUtils {

    private static final Logger LOG = Logger.getLogger(GitTestUtils.class);

    private GitTestUtils() {
    }

    public static void cleanGitUserConfig() {
        try {
            clearRecursively(SystemReader.getInstance().getUserConfig());
        } catch (Exception e) {
            LOG.warn("Unable to get Git user config");
        }
    }

    private static void clearRecursively(Config config) {
        if (config == null) {
            return;
        }
        if (config instanceof StoredConfig) {
            try {
                ((StoredConfig) config).clear();
            } catch (Exception e) {
                LOG.warnf("Unable to clear Git config %s", config);
            }
        }
        clearRecursively(config.getBaseConfig());
    }
}
