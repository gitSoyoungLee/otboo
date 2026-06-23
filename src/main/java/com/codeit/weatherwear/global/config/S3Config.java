package com.codeit.weatherwear.global.config;

import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * AWS S3 관련 설정을 구성하는 Configuration 클래스입니다. S3Client와 S3Presigner Bean을 생성하여 DI(의존성 주입)에 사용됩니다.
 */
@Configuration
public class S3Config {

  @Value("${weatherwear.storage.s3.access-key}")
  private String accessKey;

  @Value("${weatherwear.storage.s3.secret-key}")
  private String secretKey;

  @Value("${weatherwear.storage.s3.region}")
  private String region;

  // MinIO용 — 운영 환경에서는 설정하지 않으므로 null 허용
  @Value("${weatherwear.storage.s3.endpoint:#{null}}")
  private String endpoint;

  @Value("${weatherwear.storage.s3.path-style-access-enabled:false}")
  private boolean pathStyleAccessEnabled;

  /**
   * S3Client Bean 생성 - 일반적인 S3 작업(업로드, 다운로드 등)을 수행하기 위한 클라이언트
   */
  @Bean
  public S3Client s3Client() {
    S3ClientBuilder builder = S3Client.builder()
        .region(Region.of(region))
        .credentialsProvider(
            StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)));

    // endpoint가 있으면 MinIO, 없으면 실제 S3
    if (endpoint != null) {
      builder.endpointOverride(URI.create(endpoint))
          .forcePathStyle(pathStyleAccessEnabled);
    }

    return builder.build();

  }

  /**
   * S3Presigner Bean 생성 - Presigned URL을 생성할 때 사용하는 클라이언트
   */
  @Bean
  public S3Presigner s3Presigner() {
    S3Presigner.Builder builder = S3Presigner.builder()
        .region(Region.of(region))
        .credentialsProvider(StaticCredentialsProvider.create(
            AwsBasicCredentials.create(accessKey, secretKey)));

    if (endpoint != null) {
      builder.endpointOverride(URI.create(endpoint))
          .serviceConfiguration(S3Configuration.builder()
              .pathStyleAccessEnabled(pathStyleAccessEnabled)
              .build());
    }

    return builder.build();
  }
}
