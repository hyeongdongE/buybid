package com.example.saleapp.util;

import net.coobird.thumbnailator.Thumbnails;
import net.coobird.thumbnailator.geometry.Positions;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class ThumbUtil {
    public static void squareThumb(InputStream in, Path outFile, int size, String format, double quality) throws Exception {
        Files.createDirectories(outFile.getParent());
        Thumbnails.of(in)
                .size(size, size)
                .crop(Positions.CENTER)
                .outputFormat(format)
                .outputQuality(quality)
                .toFile(outFile.toFile());
    }
}