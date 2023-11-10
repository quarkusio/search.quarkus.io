package io.quarkus.search.app.testsupport;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.OrTreeFilter;
import org.eclipse.jgit.treewalk.filter.PathFilter;

class GitCopier {

    public static GitCopier create(Repository originalRepo, String originalBranch) throws IOException {
        RevWalk revWalk = new RevWalk(originalRepo);
        ObjectId original = originalRepo.resolve(originalBranch);
        if (original == null) {
            throw new IllegalStateException("Missing branch '%s' in '%s'".formatted(originalBranch, originalRepo));
        }
        return new GitCopier(originalRepo, revWalk.parseTree(original));

    }

    private final Repository originalRepo;
    private final RevTree originalTree;

    private GitCopier(Repository originalRepo, RevTree originalTree) {
        this.originalRepo = originalRepo;
        this.originalTree = originalTree;
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
            throw new IllegalStateException("Could not find some paths in original: %s"
                    .formatted(originalPathFilterToCopyPaths.keySet().stream().map(PathFilter::getPath)
                            .collect(Collectors.joining(", "))));
        }
    }

}
