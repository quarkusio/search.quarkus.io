package io.quarkus.search.app.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

import io.quarkus.logging.Log;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.eclipse.jgit.util.RawParseUtils;

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
        return revTree(repo, original);
    }

    public static RevTree revTree(Repository repo, ObjectId rev) {
        try {
            return new RevWalk(repo).parseTree(rev);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static InputStream file(Repository repo, RevTree tree, String path) {
        try {
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
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static boolean fileExists(Repository repo, RevTree tree, String path) {
        try {
            TreeWalk treeWalk = new TreeWalk(repo);
            treeWalk.addTree(tree);
            treeWalk.setRecursive(true);
            treeWalk.setFilter(PathFilter.create(path));
            return treeWalk.next();
        } catch (IOException e) {
            Log.warn("A problem occurred while trying to find a file in git tree: " + e.getMessage(), e);
            return false;
        }
    }

    public static Stream<String> fileStream(Repository repo, RevTree tree, String path, Predicate<String> filenameFilter) {
        try {
            List<String> files = new ArrayList<>();

            TreeWalk treeWalk = new TreeWalk(repo);
            treeWalk.addTree(tree);
            treeWalk.setRecursive(true);
            treeWalk.setFilter(PathFilter.create(path));

            while (treeWalk.next()) {
                byte[] rawPath = treeWalk.getRawPath();
                String fullName = RawParseUtils.decode(StandardCharsets.UTF_8, rawPath, 0, rawPath.length);
                if (filenameFilter.test(fullName)) {
                    files.add(fullName);
                }
            }
            return files.stream();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static Instant committedInstant(Git git, ObjectId revId) {
        try {
            return Instant.ofEpochSecond(git.log().add(revId).call().iterator().next().getCommitTime());
        } catch (GitAPIException | MissingObjectException | IncorrectObjectTypeException e) {
            throw new RuntimeException(e);
        }
    }
}
