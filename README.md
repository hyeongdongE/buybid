# BuyBid

중고 물품을 등록하고, 최대 금액 자동입찰 또는 즉시 구매로 거래를 확정한 뒤 채팅으로 거래를 조율하는 웹 서비스입니다. Java 21, Spring Boot, MySQL, Thymeleaf, STOMP를 사용합니다.

## 로컬 실행

JDK 21과 MySQL 8.0이 필요합니다. 로컬 MySQL에 빈 `buybid` 데이터베이스와 해당 DB에 접근할 개발 계정을 만든 뒤 아래 환경변수를 설정합니다.

| 변수 | 용도 |
| --- | --- |
| `BUYBID_DB_URL` | JDBC 주소. 개발 기본값은 `jdbc:mysql://127.0.0.1:3306/buybid` |
| `BUYBID_DB_USER`, `BUYBID_DB_PASSWORD` | MySQL 계정 |
| `JWT_SECRET` | 32바이트 이상의 난수를 Base64로 인코딩한 값 |
| `BUYBID_UPLOAD_DIR` | 이미지 저장 폴더. 개발 기본값은 `./uploads` |

PowerShell 7에서 로컬 키를 생성하고 개발 프로필로 시작할 수 있습니다.

```powershell
$env:JWT_SECRET = [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
.\gradlew.bat bootRun --args="--spring.profiles.active=dev"
```

`http://localhost:8080/main`에서 확인합니다. 첫 부팅에서 Flyway가 빈 스키마와 기본 카테고리를 구성합니다. 운영 프로필은 HTTPS를 사용하고 `BUYBID_UPLOAD_DIR`도 지정해야 합니다.

## 테스트

```powershell
.\gradlew.bat test bootJar
```

기본 테스트는 H2 메모리 DB와 테스트 전용 설정을 사용합니다. 실제 MySQL 잠금과 Flyway 마이그레이션을 검증하려면 **비어 있는 테스트 전용** MySQL DB를 사용합니다.

```powershell
$env:BUYBID_TEST_DB_URL = 'jdbc:mysql://127.0.0.1:3306/buybid_test'
$env:BUYBID_TEST_DB_USER = 'buybid_test'
$env:BUYBID_TEST_DB_PASSWORD = Read-Host -MaskInput 'Test database password'
$env:BUYBID_TEST_DDL_AUTO = 'validate'
$env:BUYBID_TEST_FLYWAY = 'true'
.\gradlew.bat test bootJar --rerun-tasks
```

GitHub Actions는 MySQL 서비스와 같은 설정으로 테스트를 실행합니다. 상품 목록은 `page`, `size`, `sort`, `category`를 받아 페이지를 반환합니다. 채팅 이력은 `nextBefore`를 다음 요청의 `before`로 보내 이전 메시지를 읽습니다.

## 기존 MySQL 데이터베이스 전환

이미 테이블이 있는 DB에는 Flyway 기준선을 자동 등록하지 않습니다. V1은 기존 스키마 형태의 기준선이며 V2가 유일 제약, 순위 컬럼과 조회 인덱스를 적용합니다.

1. 기존 DB를 백업하고 동일한 설정의 별도 사본에 복원합니다. 실제 테이블과 컬럼의 이름·타입·FK를 `V1__initial_schema.sql`과 비교합니다.
2. 사본에서 `docs/legacy-schema-preflight.sql`을 실행합니다. 중복, 참조 누락 및 가격 불일치를 검토합니다. 기존 거래의 낙찰자·금액과 중복 행은 도메인 기준에 따라 확인한 뒤 전환합니다.
3. 스키마와 데이터가 일치하는 사본에서 Flyway CLI의 `baseline` 명령으로 `baselineVersion=1`을 명시해 기록합니다. 애플리케이션을 시작해 V2 적용과 JPA 검증을 확인합니다. 문제가 있으면 원인을 확인하고 백업 사본에서 다시 시작합니다.
4. 전환 및 복원 검증을 끝낸 뒤 운영 적용을 계획합니다. MySQL DDL은 여러 DDL 문장을 하나의 롤백 가능한 트랜잭션으로 묶을 수 없으므로 백업에서 복구하는 절차를 확보합니다.

새 JWT 발급 규칙과 리프레시 토큰 저장 형식을 적용한 배포에서는 기존 브라우저 세션이 다시 로그인해야 합니다. 과거 Git 커밋에 남은 운영 자격 증명이 실제 사용됐거나 재사용됐다면 해당 자격 증명을 교체합니다.

## 검증과 면접 자료

[트러블슈팅 및 검증 수치](docs/troubleshooting.md)에서 원인, 해결 방식, 테스트 결과와 측정 범위를 확인할 수 있습니다.
