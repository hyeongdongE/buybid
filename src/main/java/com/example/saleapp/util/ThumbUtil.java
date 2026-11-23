package com.example.saleapp.util;

import net.coobird.thumbnailator.Thumbnails;
import net.coobird.thumbnailator.geometry.Positions;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;

public class ThumbUtil {
    public static void squareThumb(BufferedImage image, Path output) throws IOException {
        Thumbnails.of(image).size(400, 400).crop(Positions.CENTER)
                .imageType(BufferedImage.TYPE_INT_RGB).outputFormat("jpg").outputQuality(0.85)
                .toFile(output.toFile());
    }
}
