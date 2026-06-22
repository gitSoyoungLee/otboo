package com.codeit.weatherwear.global.processor;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class ThumnailImageProcessor implements ImageProcessor{

    private static final int CLOTH_THUMNAIL_WIDTH = 600;
    private static final int CLOTH_THUMBNAIL_HEIGHT = 400;

    /*
    1. 이미지 검증
    2. 리사이즈
    3. 결과 변환
     */
    @Override
    public ProcessedImage process(MultipartFile image) {
        try {
            return new ProcessedImage(
                image.getBytes(),
                image.getContentType(),
                "jpg",
                image.getSize()
            );
        } catch (Exception e) {
            return null;
        }
    }
}
