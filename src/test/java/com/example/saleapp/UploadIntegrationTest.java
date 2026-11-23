package com.example.saleapp;

import com.example.saleapp.config.CustomUserDetail;
import com.example.saleapp.dto.ProductDTO;
import com.example.saleapp.entity.CategoriesEntity;
import com.example.saleapp.entity.UsersEntity;
import com.example.saleapp.repository.CategoriesRepository;
import com.example.saleapp.repository.ProductImageRepository;
import com.example.saleapp.repository.ProductsRepository;
import com.example.saleapp.repository.UsersRepository;
import com.example.saleapp.service.ProductService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.support.TransactionTemplate;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class UploadIntegrationTest {
    @TempDir static Path uploadRoot;

    @DynamicPropertySource
    static void configureUploads(DynamicPropertyRegistry registry) {
        registry.add("app.upload-dir", () -> uploadRoot.toString());
    }

    @Autowired ProductService service;
    @Autowired ProductsRepository products;
    @Autowired ProductImageRepository images;
    @Autowired UsersRepository users;
    @Autowired CategoriesRepository categories;
    @Autowired TransactionTemplate transaction;

    @Test
    void corruptSecondImageLeavesNoNewProductImageRowOrFile() throws Exception {
        long productCount = products.count();
        long imageCount = images.count();
        long fileCount = storedFiles();
        var corrupt = new MockMultipartFile("images", "broken.png", "image/png", new byte[]{1, 2, 3});
        var dto = product(List.of(png(), corrupt), List.of(1, 2));
        assertThatThrownBy(() -> service.productUpload(dto, seller())).isInstanceOf(ResponseStatusException.class);
        assertThat(products.count()).isEqualTo(productCount);
        assertThat(images.count()).isEqualTo(imageCount);
        assertThat(storedFiles()).isEqualTo(fileCount);
    }

    @Test
    void validImagesPublishOnlyServerGeneratedThumbnails() throws Exception {
        long files = storedFiles();
        assertThat(service.productUpload(product(List.of(png()), List.of(1)), seller()).getStatusCode().value())
                .isEqualTo(200);
        assertThat(storedFiles() - files).isEqualTo(1);
        try (var paths = Files.walk(uploadRoot)) {
            assertThat(paths.filter(Files::isRegularFile).toList()).allMatch(path -> path.toString().endsWith(".jpg"));
        }
    }

    @Test
    void transactionRollbackRemovesPublishedThumbnail() throws Exception {
        var dto = product(List.of(png()), List.of(1));
        var seller = seller();
        long beforeFiles = storedFiles();
        long beforeProducts = products.count();
        long beforeImages = images.count();
        transaction.executeWithoutResult(status -> {
            assertThat(service.productUpload(dto, seller).getStatusCode().value()).isEqualTo(200);
            status.setRollbackOnly();
        });
        assertThat(products.count()).isEqualTo(beforeProducts);
        assertThat(images.count()).isEqualTo(beforeImages);
        assertThat(storedFiles()).isEqualTo(beforeFiles);
    }

    private long storedFiles() throws IOException {
        try (var paths = Files.walk(uploadRoot)) {
            return paths.filter(Files::isRegularFile).count();
        }
    }

    private ProductDTO product(List<MultipartFile> uploads, List<Integer> orders) {
        var category = categories.save(CategoriesEntity.builder().name(UUID.randomUUID().toString()).build());
        var now = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
        return ProductDTO.builder().title("fixture item").description("fixture description").category(category.getName())
                .startAt(now.minusMinutes(10)).endAt(now.plusHours(1)).startPrice(1000).minIncrement(100)
                .buyNow(5000).images(uploads).orders(orders).build();
    }

    private MockMultipartFile png() throws IOException {
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB), "png", bytes);
        return new MockMultipartFile("images", "fixture.png", "image/png", bytes.toByteArray());
    }

    private CustomUserDetail seller() {
        var name = UUID.randomUUID().toString();
        return new CustomUserDetail(users.save(UsersEntity.builder().name(name).email(name + "@example.test")
                .password("unused").role("ROLE_USER").created_at(LocalDateTime.now()).build()));
    }
}
