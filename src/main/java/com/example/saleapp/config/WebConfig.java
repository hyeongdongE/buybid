package com.example.saleapp.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;
import java.nio.file.Path;
import java.util.regex.Pattern;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    private static final Pattern THUMBNAIL = Pattern.compile("[0-9]+_400/[0-9]+\\.jpg");
    private final String thumbnailRoot;

    public WebConfig(@Value("${app.upload-dir}") String uploadDir) {
        String uri = Path.of(uploadDir).toAbsolutePath().normalize().resolve("products").toUri().toString();
        thumbnailRoot = uri.endsWith("/") ? uri : uri + "/";
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/files/products/**")
                .addResourceLocations(thumbnailRoot).resourceChain(true).addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        return THUMBNAIL.matcher(resourcePath).matches() ? super.getResource(resourcePath, location) : null;
                    }
                });
    }
}
