package com.almightyalpaca.jetbrains.plugins.discord.unusedicons;

import com.almightyalpaca.jetbrains.plugins.discord.util.RepoReader;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class UnusedIconReporter
{
    public static void main(String[] args)
    {
        new UnusedIconReporter().start();
    }

    public void start()
    {
        try
        {
            Path icons = Paths.get("icons/").toAbsolutePath();
            final JsonParser parser = new JsonParser();

            Set<Path> paths = Files.list(icons)
                    .filter(path -> Files.isDirectory(path))
                    .map(path -> Pair.of(path, Paths.get(path.toAbsolutePath().toString() + ".json")))
                    .filter(pair -> Files.exists(pair.getRight()))
                    .flatMap(pair -> {
                        try
                        {
                            JsonObject object = parser
                                    .parse(Files.newBufferedReader(pair.getRight(), StandardCharsets.UTF_8))
                                    .getAsJsonObject();
                            return StreamSupport.stream(object.get("icons").getAsJsonArray().spliterator(), false)
                                    .map(JsonElement::getAsJsonObject)
                                    .map(o -> o.get("asset"))
                                    .map(JsonElement::getAsString)
                                    .map(asset -> pair.getLeft().resolve(asset + ".png"));
                        }
                        catch (IOException e)
                        {
                            e.printStackTrace();
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            Collection<RepoReader.Theme> themes = RepoReader.getThemes(icons);

            for (RepoReader.Theme theme : themes)
            {
                Files.list(theme.getFolder())
                        .filter(Files::isRegularFile)
                        .filter(path -> FilenameUtils.getExtension(path.toString()).equalsIgnoreCase("png"))
                        .filter(path -> !StringUtils.endsWithIgnoreCase(path.toString(), "_low.png"))
                        .distinct()
                        .sorted()
                        .filter(path -> !path.getFileName().toString().equalsIgnoreCase("unknown.png"))
                        .filter(path -> !paths.contains(path))
                        .forEach(path -> System.out.println(
                                path.getName(path.getNameCount() - 3) + "/" + path.getName(path.getNameCount() - 2) + "/" +
                                path.getName(path.getNameCount() - 1)));
            }
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }
}
