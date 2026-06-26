package com.example.explosionlib.platform.services;

import java.nio.file.Path;

public interface IPlatformHelper {
    String getPlatformName();

    boolean isDevelopmentEnvironment();

    default String getEnvironmentName() {
        return isDevelopmentEnvironment() ? "development" : "production";
    }

    Path getConfigDirectory();
}
