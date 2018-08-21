package com.almightyalpaca.jetbrains.plugins.discord.uploader;

import okhttp3.OkHttpClient;
import org.apache.commons.collections4.SetValuedMap;
import org.apache.commons.io.FilenameUtils;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@SuppressWarnings("Duplicates")
public class Main
{
    @NotNull
    private static final String TOKEN = Objects.requireNonNull(System.getenv("DISCORD_TOKEN"));

    public static void main(String[] args)
    {
        new Main().start();
    }

    public void start()
    {
        System.out.println("Starting icon sync");

        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(60L, TimeUnit.MINUTES)
                .writeTimeout(60L, TimeUnit.MINUTES)
                .readTimeout(60L, TimeUnit.MINUTES)
                .build();

        try
        {
            GitChangeProvider changeProvider = new GitChangeProvider();
            Mode mode = changeProvider.getMode();

            Path icons = Paths.get("icons/").toAbsolutePath();
            Collection<RepoReader.Theme> themes = RepoReader.getThemes(icons);

            System.out.println("Mode: " + mode.name().toLowerCase());

            switch (mode)
            {
                case ALL:
                    this.processAll(client, themes, icons);
                    break;
                case CHANGES:
                    this.processChanges(client, changeProvider, themes, icons);
                    break;
                case NONE:
                    break;
            }
        }
        catch (IOException | GitAPIException e)
        {
            e.printStackTrace();
        }

        client.dispatcher().executorService().shutdown();
        client.connectionPool().evictAll();
    }

    private void processAll(@NotNull OkHttpClient client, @NotNull Collection<RepoReader.Theme> themes, @NotNull Path icons)
            throws IOException
    {
        Objects.requireNonNull(client, "client");
        Objects.requireNonNull(themes, "themes");
        Objects.requireNonNull(icons, "icons");

        for (RepoReader.Theme theme : themes)
        {
            DiscordApplication application = new DiscordApplication(client, theme.getApplication(), TOKEN);
            SetValuedMap<String, String> uploadedAssets = application.getUploadedAssets();

            Collection<Path> paths = Files.list(theme.getFolder())
                    .filter(Files::isRegularFile)
                    .filter(path -> FilenameUtils.getExtension(path.toString()).equalsIgnoreCase("png"))
                    .distinct()
                    .sorted()
                    .collect(Collectors.toList());

            for (Path path : paths)
            {
                String name = FilenameUtils.getBaseName(path.getFileName().toString());

                try
                {
                    System.out.println("adding " + name);
                    application.uploadAsset(icons.getParent().resolve(path), name);

                    for (String id : uploadedAssets.get(name))
                    {
                        System.out.println("removing " + name + " (" + id + ")");
                        try
                        {
                            application.deleteAsset(id);
                        }
                        catch (IOException e)
                        {
                            e.printStackTrace();
                        }
                    }
                }
                catch (IOException e)
                {
                    e.printStackTrace();
                }
            }
        }
    }

    private void processChanges(@NotNull OkHttpClient client, @NotNull GitChangeProvider changeProvider,
                                @NotNull Collection<RepoReader.Theme> themes, @NotNull Path icons)
            throws GitAPIException, IOException
    {
        Objects.requireNonNull(client, "client");
        Objects.requireNonNull(changeProvider, "changeProvider");
        Objects.requireNonNull(themes, "themes");
        Objects.requireNonNull(icons, "icons");

        GitChangeProvider.Changes changes = changeProvider.getChangesSinceLastCommit();

        for (RepoReader.Theme theme : themes)
        {
            GitChangeProvider.Changes themeChanges = changes.getChanges(theme.getId());
            DiscordApplication application = new DiscordApplication(client, theme.getApplication(), TOKEN);
            SetValuedMap<String, String> uploadedAssets = application.getUploadedAssets();

            for (Path path : themeChanges.getAdded())
            {
                String name = FilenameUtils.getBaseName(path.getFileName().toString());

                System.out.println("adding " + name);
                try
                {
                    application.uploadAsset(icons.getParent().resolve(path), name);
                }
                catch (IOException e)
                {
                    e.printStackTrace();
                }
            }

            for (Path path : themeChanges.getRemoved())
            {
                String name = FilenameUtils.getBaseName(path.getFileName().toString());

                for (String id : uploadedAssets.get(name))
                {
                    System.out.println("removing " + name + " (" + id + ")");
                    try
                    {
                        application.deleteAsset(id);
                    }
                    catch (IOException e)
                    {
                        e.printStackTrace();
                    }
                }
            }
        }
    }
}
