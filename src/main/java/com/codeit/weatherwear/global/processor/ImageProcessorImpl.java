package com.codeit.weatherwear.global.processor;

import com.codeit.weatherwear.global.exception.CustomException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class ImageProcessorImpl implements ImageProcessor{

    @Override
    public ProcessedImage process(MultipartFile image, ImageProcessingType type) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

            Thumbnails.of(image.getInputStream())
                // type별 목표 크기로 리사이즈
                .size(type.getWidth(), type.getHeight())
                // 비율 유지
                .keepAspectRatio(true)
                // 출력 포맷을 jpg로 고정
                .outputFormat("jpg")
                .toOutputStream(outputStream);

            byte[] bytes = outputStream.toByteArray();

            return new ProcessedImage(
                bytes,
                "image/jpeg",  // Content-Type
                "jpg",         // 확장자
                bytes.length   // 변환 후 용량
            );

        } catch (IOException e) {
            throw new ImageProcessingException();
        }
    }
}
