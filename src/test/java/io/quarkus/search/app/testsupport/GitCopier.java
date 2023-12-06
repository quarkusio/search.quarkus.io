package io.quarkus.search.app.testsupport;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.quarkus.search.app.util.GitUtils;

import io.quarkus.logging.Log;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.OrTreeFilter;
import org.eclipse.jgit.treewalk.filter.PathFilter;

class GitCopier {

    public static GitCopier create(Repository originalRepo, boolean failOnMissing, String... originalRefs) throws IOException {
        return new GitCopier(originalRepo, GitUtils.firstExistingRevTree(originalRepo, originalRefs), failOnMissing);
    }

    private final Repository originalRepo;
    private final RevTree originalTree;
    private final boolean failOnMissing;

    private GitCopier(Repository originalRepo, RevTree originalTree, boolean failOnMissing) {
        this.originalRepo = originalRepo;
        this.originalTree = originalTree;
        this.failOnMissing = failOnMissing;
    }

    public void copy(Path copyRootPath, Map<String, String> copyPathToOriginalPath) throws IOException, GitAPIException {
        Map<PathFilter, List<Path>> originalPathFilterToCopyPaths = copyPathToOriginalPath.entrySet().stream()
                .collect(Collectors.groupingBy(
                        e -> PathFilter.create(e.getValue()),
                        Collectors.mapping(e -> copyRootPath.resolve(e.getKey()), Collectors.toList())));
        TreeWalk treeWalk = new TreeWalk(originalRepo);
        treeWalk.addTree(originalTree);
        treeWalk.setRecursive(true);
        treeWalk.setFilter(
                OrTreeFilter.create(originalPathFilterToCopyPaths.keySet().toArray(new PathFilter[0])));
        while (treeWalk.next()) {
            for (Iterator<Map.Entry<PathFilter, List<Path>>> iterator = originalPathFilterToCopyPaths.entrySet()
                    .iterator(); iterator.hasNext();) {
                Map.Entry<PathFilter, List<Path>> entry = iterator.next();
                var originalPathFilter = entry.getKey();
                if (originalPathFilter.matchFilter(treeWalk) == 0) {
                    ObjectId objectId = treeWalk.getObjectId(0);
                    ObjectLoader loader = originalRepo.open(objectId);
                    for (Path copyPath : entry.getValue()) {
                        Files.createDirectories(copyPath.getParent());
                        try (var out = Files.newOutputStream(copyPath, StandardOpenOption.CREATE_NEW)) {
                            loader.copyTo(out);
                        }
                    }
                    iterator.remove();
                    break;
                }
            }
        }
        if (!originalPathFilterToCopyPaths.isEmpty()) {
            String message = "Could not find some paths in original %s: %s"
                    .formatted(
                            originalTree.getName(),
                            originalPathFilterToCopyPaths.keySet().stream().map(PathFilter::getPath)
                                    .collect(Collectors.joining(", ")));
            if (failOnMissing) {
                throw new IllegalStateException(message);
            } else {
                Log.error(message);
            }
        }
    }

}
