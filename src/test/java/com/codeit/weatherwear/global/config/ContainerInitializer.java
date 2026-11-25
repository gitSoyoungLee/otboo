package com.codeit.weatherwear.global.config;

import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.kafka.KafkaContainer;

public class ContainerInitializer implements
    ApplicationContextInitializer<ConfigurableApplicationContext> {

  private static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:17-alpine")
          .withDatabaseName("weatherwear")
          .withUsername("user")
          .withPassword("password")
          .withInitScript("schema.sql");

  private static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:4.0.0");

  // 비밀번호 초기화 요청 횟수 제한 등 Redis를 실제로 사용하는 기능을 검증하기 위한 컨테이너
  private static final GenericContainer<?> REDIS =
      new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

  static {
    POSTGRES.start();
    KAFKA.start();
    REDIS.start();
  }

  @Override
  public void initialize(ConfigurableApplicationContext applicationContext) {
    TestPropertyValues.of(
        "spring.datasource.url=" + POSTGRES.getJdbcUrl(),
        "spring.datasource.username=" + POSTGRES.getUsername(),
        "spring.datasource.password=" + POSTGRES.getPassword(),
        "spring.kafka.bootstrap-servers=" + KAFKA.getBootstrapServers(),
        "spring.data.redis.host=" + REDIS.getHost(),
        "spring.data.redis.port=" + REDIS.getMappedPort(6379)
    ).applyTo(applicationContext.getEnvironment());
  }
}
