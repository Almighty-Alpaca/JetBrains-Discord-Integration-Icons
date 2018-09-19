package com.almightyalpaca.jetbrains.plugins.discord.icons;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.commons.lang3.tuple.Pair;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.stream.StreamSupport;

public class MissingIconReporter
{
    public static void main(String[] args)
    {
        new MissingIconReporter().start();
    }

    public void start()
    {
        try
        {
            Path icons = Paths.get("icons/").toAbsolutePath();
            final JsonParser parser = new JsonParser();

            Files.list(icons)
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
                    .filter(path -> !Files.isRegularFile(path))
                    .forEach(path -> System.out.println("Missing icon: " + path.getName(path.getNameCount() - 3) + "/" +
                            path.getName(path.getNameCount() - 2) + "/" + path.getName(path.getNameCount() - 1)));
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }
}
