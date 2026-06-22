package com.codeit.weatherwear.global.processor;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class ThumnailImageProcessor implements ImageProcessor{


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
