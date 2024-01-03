package io.quarkus.search.app.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;

public final class GitUtils {

    private GitUtils() {
    }

    public static RevTree firstExistingRevTree(Repository repo, String... originalRefs) throws IOException {
        ObjectId original = null;
        for (String ref : originalRefs) {
            original = repo.resolve(ref);
            if (original != null) {
                break;
            }
        }
        if (original == null) {
            throw new IllegalStateException("None of the refs '%s' exist in '%s'"
                    .formatted(Arrays.toString(originalRefs), repo));
        }
        return new RevWalk(repo).parseTree(original);
    }

    public static InputStream file(Repository repo, RevTree tree, String path) throws IOException {
        TreeWalk treeWalk = new TreeWalk(repo);
        treeWalk.addTree(tree);
        treeWalk.setRecursive(true);
        treeWalk.setFilter(PathFilter.create(path));
        if (!treeWalk.next()) {
            throw new IllegalStateException("Missing file '%s' in '%s'".formatted(path, tree));
        }
        ObjectId objectId = treeWalk.getObjectId(0);
        ObjectLoader loader = repo.open(objectId);
        return loader.openStream();
    }

    public static boolean fileExists(Repository repo, RevTree tree, String path) throws IOException {
        TreeWalk treeWalk = new TreeWalk(repo);
        treeWalk.addTree(tree);
        treeWalk.setRecursive(true);
        treeWalk.setFilter(PathFilter.create(path));
        return treeWalk.next();
    }
}
