package com.example.saleapp.service;

import com.example.saleapp.util.ThumbUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

@Component
@Slf4j
class ProductImageStorage {
    private static final long MAX_PIXELS = 12_000_000;
    private static final Set<String> FORMATS = Set.of("jpeg", "png", "webp");
    private final Path root;

    ProductImageStorage(@Value("${app.upload-dir}") String uploadDir) {
        root = Path.of(uploadDir).toAbsolutePath().normalize();
    }

    Path stage() throws IOException {
        return Files.createTempDirectory(Files.createDirectories(root.resolve(".staging")), "product-");
    }

    void thumbnail(MultipartFile file, Path target) throws IOException {
        BufferedImage image = readImage(file);
        try {
            ThumbUtil.squareThumb(image, target);
        } finally {
            image.flush();
        }
    }

    private BufferedImage readImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw invalidImage(null);
        }
        try (var stream = file.getInputStream();
             var input = ImageIO.createImageInputStream(stream)) {
            if (input == null) throw invalidImage(null);
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw invalidImage(null);
            var reader = readers.next();
            try {
                if (!FORMATS.contains(reader.getFormatName().toLowerCase(Locale.ROOT))) {
                    throw invalidImage(null);
                }
                reader.setInput(input, true, true);
                if ((long) reader.getWidth(0) * reader.getHeight(0) > MAX_PIXELS) {
                    throw invalidImage(null);
                }
                return reader.read(0);
            } finally {
                reader.dispose();
            }
        } catch (IOException | IllegalArgumentException exception) {
            throw invalidImage(exception);
        }
    }

    void publish(Path staged, long productId) throws IOException {
        Path images = Files.createDirectories(root.resolve("products"));
        Path destination = images.resolve(productId + "_400");
        Files.move(staged, destination);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) clean(destination);
            }
        });
    }

    void clean(Path directory) {
        if (!Files.exists(directory)) return;
        try (var files = Files.list(directory)) {
            for (Path path : files.toList()) Files.deleteIfExists(path);
        } catch (IOException exception) {
            log.warn("상품 이미지 임시 파일을 정리하지 못했습니다. directory={}", directory, exception);
        }
        try {
            Files.deleteIfExists(directory);
        } catch (IOException exception) {
            log.warn("상품 이미지 디렉터리를 정리하지 못했습니다. directory={}", directory, exception);
        }
    }

    private ResponseStatusException invalidImage(Exception cause) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "손상되지 않은 JPEG, PNG 또는 WebP 이미지를 등록하세요. 최대 1200만 픽셀을 지원합니다.", cause);
    }
}
