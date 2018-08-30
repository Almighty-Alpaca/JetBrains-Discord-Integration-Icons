package com.almightyalpaca.jetbrains.plugins.discord.icongenerator;

import com.almightyalpaca.jetbrains.plugins.discord.util.RepoReader;
import com.googlecode.pngtastic.core.PngImage;
import com.googlecode.pngtastic.core.PngOptimizer;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class IconGenerator
{
    private static final float QUALITY = 0.9f;
    private static final int SIZE = 256;

    public static void main(String[] args)
    {
        try
        {
            final Mode mode;

            if (Arrays.stream(args).anyMatch(arg -> StringUtils.equalsIgnoreCase(arg, "ForceAll")))
                mode = Mode.ALL;
            else
                mode = Mode.MISSING;

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
                        .filter(path -> FilenameUtils.getExtension(path.toString()).equalsIgnoreCase("png"))
                        .filter(path -> !StringUtils.endsWithIgnoreCase(path.toString(), "_low.png"))
                        .distinct()
                        .sorted()
                        .collect(Collectors.toList());

                for (Path path : paths)
                {
                    Future<?> task = pool.submit(() -> {
                        String name = FilenameUtils.getBaseName(path.getFileName().toString());
                        Path newPath = path.resolveSibling(name + "_low.png");

                        if (mode == Mode.MISSING && Files.exists(newPath))
                        {
                            System.out.println("Skipping " + theme.getId() + "/" + path.getFileName());
                            return;
                        }

                        System.out.println("Starting with " + theme.getId() + "/" + path.getFileName());

                        ImageWriter writer = ImageIO.getImageWritersByFormatName("png").next();
                        ImageWriteParam param = writer.getDefaultWriteParam();
                        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                        param.setCompressionQuality(QUALITY);

                        try
                        {
                            BufferedImage image = ImageIO.read(Files.newInputStream(path));

                            RenderedImage scaledImage = getRenderImage(image.getScaledInstance(SIZE, SIZE, Image.SCALE_SMOOTH));

                            ByteArrayOutputStream byteArrayStream = new ByteArrayOutputStream();
                            ImageOutputStream imageOutputStream = ImageIO.createImageOutputStream(byteArrayStream);

                            writer.setOutput(imageOutputStream);
                            writer.write(null, new IIOImage(scaledImage, null, null), param);

                            final PngImage pngImage = new PngImage(new ByteArrayInputStream(byteArrayStream.toByteArray()));
                            final PngOptimizer optimizer = new PngOptimizer();
                            final PngImage optimizedPngImage = optimizer.optimize(pngImage);

                            OutputStream outputStream = Files.newOutputStream(newPath,
                                    StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE);
                            optimizedPngImage.writeDataOutputStream(outputStream);
                        }
                        catch (IOException e)
                        {
                            e.printStackTrace();
                        }

                        System.out.println("Finished " + theme.getId() + "/" + path.getFileName());
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

    private static RenderedImage getRenderImage(Image image)
    {
        if (image instanceof RenderedImage)
            return (RenderedImage) image;

        BufferedImage bimage = new BufferedImage(image.getWidth(null), image.getHeight(null), BufferedImage.TYPE_INT_ARGB);

        // Draw the image on to the buffered image
        Graphics2D bGr = bimage.createGraphics();
        bGr.drawImage(image, 0, 0, null);
        bGr.dispose();

        // Return the buffered image
        return bimage;
    }
}
