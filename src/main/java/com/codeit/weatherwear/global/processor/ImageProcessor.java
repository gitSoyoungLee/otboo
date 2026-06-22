package com.codeit.weatherwear.global.processor;

import org.springframework.web.multipart.MultipartFile;

/*
이미지 처리 규약
 */
public interface ImageProcessor {
    ProcessedImage process(MultipartFile image, ImageProcessingType type);
}
