package com.almightyalpaca.jetbrains.plugins.discord.uploader;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryBuilder;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class GitChangeProvider
{
    private final Repository repo;
    private final Git git;

    public GitChangeProvider() throws IOException
    {
        this.repo = new RepositoryBuilder()
                .findGitDir(new File(System.getProperty("user.dir") + "/.git"))
                .setMustExist(true)
                .build();

        this.git = Git.wrap(repo);
    }

    public Mode getMode() throws IOException
    {
        String message = repo.parseCommit(repo.resolve("HEAD")).getFullMessage();

        if (StringUtils.containsIgnoreCase(message, "CiForceAll"))
            return Mode.ALL;
        else if (StringUtils.containsIgnoreCase(message, "CiForceNone"))
            return Mode.NONE;
        else
            return Mode.CHANGES;
    }

    @SuppressWarnings("SameParameterValue")
    @NotNull
    public Changes getChangesSinceLastCommit() throws GitAPIException, IOException
    {
        ObjectId idNew = repo.resolve("HEAD");
        ObjectId idOld = repo.resolve("HEAD^1");

        ObjectReader reader = repo.newObjectReader();

        CanonicalTreeParser newTreeIter = new CanonicalTreeParser();
        newTreeIter.reset(reader, repo.parseCommit(idNew).getTree());
        CanonicalTreeParser oldTreeIter = new CanonicalTreeParser();
        oldTreeIter.reset(reader, repo.parseCommit(idOld).getTree());

        List<DiffEntry> listDiffs = git
                .diff()
                .setOldTree(oldTreeIter)
                .setNewTree(newTreeIter)
                .call();

        Set<String> removedFiles = new HashSet<>();
        Set<String> addedFiles = new HashSet<>();

        for (DiffEntry diff : listDiffs)
        {
            switch (diff.getChangeType())
            {
                case ADD:
                    addedFiles.add(diff.getNewPath());
                    break;
                case COPY:
                    addedFiles.add(diff.getNewPath());
                    break;
                case DELETE:
                    removedFiles.add(diff.getOldPath());
                    break;
                case MODIFY:
                    removedFiles.add(diff.getOldPath());
                    addedFiles.add(diff.getNewPath());
                    break;
                case RENAME:
                    removedFiles.add(diff.getOldPath());
                    addedFiles.add(diff.getNewPath());
                    break;
                default:
                    System.out.println("Unknown ChangeType: " + diff.getChangeType());
                    break;
            }
        }

        Collection<Path> removedPaths = removedFiles.stream()
                .filter(path -> FilenameUtils.getExtension(path).equalsIgnoreCase("png"))
                .map(Paths::get)
                .filter(path -> path.startsWith("icons"))
                .map(Path::toAbsolutePath)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
        Collection<Path> addedPaths = addedFiles.stream()
                .filter(path -> FilenameUtils.getExtension(path).equalsIgnoreCase("png"))
                .map(Paths::get)
                .map(Path::toAbsolutePath)
                .distinct()
                .sorted()
                .collect(Collectors.toList());

        return new Changes(removedPaths, addedPaths);
    }

    static class Changes
    {
        @NotNull
        private final Collection<Path> removed, added;

        public Changes(@NotNull Collection<Path> removed, @NotNull Collection<Path> added)
        {
            this.removed = Objects.requireNonNull(removed, "removed");
            this.added = Objects.requireNonNull(added, "added");
        }

        @NotNull
        public Collection<Path> getRemoved()
        {
            return removed;
        }

        @NotNull
        public Collection<Path> getAdded()
        {
            return added;
        }

        @NotNull
        public Changes getChanges(@NotNull String themeId)
        {
            Objects.requireNonNull(themeId, "themeId");

            Collection<Path> removedPaths = removed.stream()
                    .filter(path -> path.getParent().getFileName().toString().equals(themeId))
                    .distinct()
                    .sorted()
                    .collect(Collectors.toList());
            Collection<Path> addedPaths = added.stream()
                    .filter(path -> path.getParent().getFileName().toString().equals(themeId))
                    .distinct()
                    .sorted()
                    .collect(Collectors.toList());

            return new Changes(removedPaths, addedPaths);
        }
    }
}
