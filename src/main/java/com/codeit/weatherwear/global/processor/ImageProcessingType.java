package com.codeit.weatherwear.global.processor;

public enum ImageProcessingType {
    CLOTH(600, 400),
    PROFILE(100, 120);

    private final int width;
    private final int height;

    ImageProcessingType(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public int getWidth() { return width; }
    public int getHeight() { return height; }

}
