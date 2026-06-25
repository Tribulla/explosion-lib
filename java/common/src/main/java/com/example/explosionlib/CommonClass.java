package com.example.explosionlib;

import com.example.explosionlib.platform.Services;

public class CommonClass {
    public static void init() {
        Constants.LOG.info("[{}] common init (platform: {})",
            Constants.MOD_ID, Services.PLATFORM.getPlatformName());
    }
}
