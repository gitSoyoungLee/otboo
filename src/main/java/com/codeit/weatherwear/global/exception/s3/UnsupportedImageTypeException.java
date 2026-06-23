package com.codeit.weatherwear.global.exception.s3;

import com.codeit.weatherwear.global.exception.CustomException;
import com.codeit.weatherwear.global.exception.ErrorCode;

/**
 * 업로드한 파일이 이미지 형식이 아니거나 지원하지 않는 확장자일 때 발생.
 * (S3 업로드 자체의 실패가 아닌, 클라이언트 입력 문제 → 400)
 */
public class UnsupportedImageTypeException extends CustomException {

  public UnsupportedImageTypeException() {
    super(ErrorCode.UNSUPPORTED_IMAGE_TYPE);
  }
}
