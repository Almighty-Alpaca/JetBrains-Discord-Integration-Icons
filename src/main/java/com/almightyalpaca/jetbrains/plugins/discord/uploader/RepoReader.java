package com.almightyalpaca.jetbrains.plugins.discord.uploader;

import com.google.gson.JsonParser;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Objects;
import java.util.stream.Collectors;

public class RepoReader
{
    @NotNull
    public static Collection<Theme> getThemes(@NotNull Path icons) throws IOException
    {
        Objects.requireNonNull(icons, "icons");

        final JsonParser parser = new JsonParser();

        return Files.list(icons)
                .filter(path -> Files.isDirectory(path))
                .map(path -> Pair.of(path, Paths.get(path.toAbsolutePath().toString() + ".json")))
                .filter(pair -> Files.exists(pair.getRight()))
                .map(pair -> {
                    try
                    {
                        return new Theme(pair.getLeft(), parser
                                .parse(Files.newBufferedReader(pair.getRight(), StandardCharsets.UTF_8))
                                .getAsJsonObject().get("application").getAsString());
                    }
                    catch (IOException e)
                    {
                        e.printStackTrace();
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    public static class Theme implements Comparable<Theme>
    {
        @NotNull
        private final Path folder;
        @NotNull
        private final String application;

        public Theme(@NotNull Path folder, @NotNull String application)
        {
            this.folder = Objects.requireNonNull(folder, "folder");
            this.application = Objects.requireNonNull(application, "application");
        }

        @NotNull
        public Path getFolder()
        {
            return folder;
        }

        @NotNull
        public String getApplication()
        {
            return application;
        }

        @NotNull
        public String getId()
        {
            return folder.getFileName().toString();
        }

        @Override
        public int compareTo(@NotNull RepoReader.Theme that)
        {
            if (this.getId().equals("classic"))
                return -1;
            if (that.getId().equals("classic"))
                return 1;
            else
                return this.getId().compareTo(that.getId());
        }
    }
}
