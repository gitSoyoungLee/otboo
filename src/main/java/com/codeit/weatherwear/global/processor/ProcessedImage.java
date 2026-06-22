package com.codeit.weatherwear.global.processor;

/**
 * 처리된 이미지 결과물
 */
public record ProcessedImage(
    byte[] bytes,
    String contentType, // ex "image/webp"
    String extension, // ex "webp"
    long size   // 변환 후 바이트 수
) {

}
