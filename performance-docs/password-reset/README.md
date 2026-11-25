# 비밀번호 초기화 장애 재현·개선 자료

- `decision-log.md` : [결정] 지점에서 본인이 고른 것과 발언 원문, git blame으로 확인한 작성자 정보
- `evidence/` : 테스트 실행 증거 (번호 순서 = 진행 순서)

| 번호 | 내용 | 결과 |
|---|---|---|
| 00 | 장애 1 첫 재현 시도 | **거짓 통과** (첫 메일 발송 지연 > 3초 관찰 시간) |
| 01 | 장애 1 재현 (워밍업 추가) | 실패: 롤백됐는데 메일 1통 수신 |
| 02 | 장애 1 수정 후 (AFTER_COMMIT) | 통과 |
| 03 | 장애 2-1·2-2·2-3 재현 | 3개 모두 실패 |
| 04 | 2-2 메일 실패 로그 수정 후 | 2-2만 통과 (2-1·2-3은 [결정] 전) |
| 05 | 요청 횟수 제한 재현 | 2개 모두 실패 |
| 06 | A + C 구현 후 | 인증·보안 테스트 24개 모두 통과 |

각 번호마다 `.png`(리포트 캡처), `.txt`(Gradle 실행 로그), `.xml`(JUnit 결과와 앱 로그), `-report-*/`(Gradle HTML 리포트)가 있다.
로컬 환경(i7-1165G7, RAM 15.7GB, Windows 11, JDK 17.0.10, Docker Desktop + Testcontainers)에서 실행했다.

## 테스트 다시 돌리기

dev 브랜치의 `S3ImageStorageTest`는 이미지 리팩토링 이후 컴파일되지 않는다(이미지 작업 범위라 수정하지 않음).
인증 테스트만 돌릴 때는 아래 init 스크립트로 이 파일을 제외한다.

```groovy
// exclude-broken-s3-test.gradle (저장소 밖에 두고 사용)
allprojects {
    plugins.withId('java') {
        sourceSets.test.java.exclude '**/global/s3/S3ImageStorageTest.java'
    }
}
```

```
./gradlew -I <경로>/exclude-broken-s3-test.gradle test --tests "com.codeit.weatherwear.domain.auth.*" --tests "com.codeit.weatherwear.domain.security.*"
```

Docker가 실행 중이어야 한다(Postgres, Kafka, Redis 컨테이너 사용).
