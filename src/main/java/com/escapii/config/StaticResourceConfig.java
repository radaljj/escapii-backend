package com.escapii.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class StaticResourceConfig implements WebMvcConfigurer {

    @Value("${app.uploads.dir:uploads}")
    private String uploadsDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = uploadsDir.startsWith("/") || uploadsDir.contains(":")
                ? "file:" + uploadsDir + "/"
                : "file:" + System.getProperty("user.dir") + "/" + uploadsDir + "/";
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(location);
    }
}
