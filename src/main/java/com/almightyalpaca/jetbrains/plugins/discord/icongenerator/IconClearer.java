package com.almightyalpaca.jetbrains.plugins.discord.icongenerator;

import com.almightyalpaca.jetbrains.plugins.discord.util.RepoReader;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class IconClearer
{
    public static void main(String[] args)
    {
        try
        {
            Path icons = Paths.get("icons/").toAbsolutePath();
            Collection<RepoReader.Theme> themes = RepoReader.getThemes(icons);

            ThreadPoolExecutor pool = new ThreadPoolExecutor(Runtime.getRuntime().availableProcessors(),
                    Runtime.getRuntime().availableProcessors() * 2,
                    10L, TimeUnit.SECONDS,
                    new LinkedBlockingQueue<>());

            List<Future<?>> tasks = new ArrayList<>();

            for (RepoReader.Theme theme : themes)
            {
                Collection<Path> paths = Files.list(theme.getFolder())
                        .filter(Files::isRegularFile)
                        .filter(path -> StringUtils.endsWithIgnoreCase(path.toString(), "_low.png"))
                        .distinct()
                        .sorted()
                        .collect(Collectors.toList());

                for (Path path : paths)
                {
                    Future<?> task = pool.submit(() -> {
                        System.out.println("Deleting " + path.getParent().getFileName() + "/" + path.getFileName());

                        try
                        {
                            Files.deleteIfExists(path);
                        }
                        catch (IOException e)
                        {
                            e.printStackTrace();
                        }
                    });

                    tasks.add(task);
                }
            }

            for (Future<?> task : tasks)
            {
                try
                {
                    task.get();
                }
                catch (ExecutionException | InterruptedException e)
                {
                    e.printStackTrace();
                }
            }

            pool.shutdown();
            pool.awaitTermination(1, TimeUnit.MINUTES);
            pool.shutdownNow();


        }
        catch (IOException | InterruptedException e)
        {
            e.printStackTrace();
        }
    }
}
