package com.codeit.weatherwear.global.processor;

import com.codeit.weatherwear.global.exception.CustomException;
import com.codeit.weatherwear.global.exception.ErrorCode;
import java.util.Map;
import java.util.UUID;

public class ImageProcessingException extends CustomException {

    public ImageProcessingException() {
        super(ErrorCode.IMAGE_PROCESSING_FAIL);
    }

}
