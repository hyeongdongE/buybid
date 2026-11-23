# BuyBid 트러블슈팅과 검증 수치

BuyBid는 중고 상품을 자동입찰 또는 즉시 구매로 거래 확정하고 구매자·판매자가 채팅으로 거래를 조율하는 서비스입니다. 최초 검수 기준은 `376d473`이며, 개선 코드는 `improve/buybid-backend` 브랜치에 있습니다.

## 1. 동시에 즉시 구매하면 낙찰자가 바뀌거나 채팅방이 중복될 수 있었다

- **현상** — 서로 다른 구매 요청이 같은 상품 상태를 읽고 각자 낙찰 정보를 쓸 수 있었습니다. 상품 종료와 채팅방 생성은 따로 저장돼 중간 실패 시 둘의 상태가 어긋날 수 있었습니다.
- **원인** — 상품 상태 확인과 변경 사이의 경합을 제어할 장치가 없었고, 즉시 구매의 변경이 하나의 서비스 트랜잭션에 포함되지 않았습니다.
- **해결** — 입찰, 즉시 구매, 만료 스케줄러가 상품 행을 잠근 뒤 상태와 시각을 다시 확인합니다. 낙찰 확정과 채팅방 생성을 같은 트랜잭션으로 묶고, DB에 상품별 채팅방 유일 제약을 적용했습니다. 스케줄러는 상품별로 처리해 한 상품의 실패가 다른 만료 상품의 처리를 막지 않도록 했습니다.
- **검증** — MySQL 8.0.46에서 상품마다 두 구매 요청을 동시에 시작하는 시나리오를 **10회** 실행했습니다. 매회 성공 **1건**, 상태 충돌 **1건**, 채팅방 **1개**였고 구매자와 낙찰자가 일치했습니다. 강제 트랜잭션 롤백에서는 상품이 진행 상태로 돌아가고 채팅방이 남지 않는 것을 검증합니다.
- **설계 이유** — 같은 상품의 상태 전이를 순서대로 처리해야 하므로 상품 행 비관적 잠금을 사용했습니다. 잠금 범위는 해당 상품입니다. 다른 상품은 계속 처리할 수 있지만 같은 인기 상품에 요청이 몰리면 잠금 대기가 발생할 수 있습니다. 지연시간과 최대 처리량은 이 테스트에서 측정하지 않았습니다.

관련 코드 [ProductService.java](../src/main/java/com/example/saleapp/service/ProductService.java) · [ProductsRepository.java](../src/main/java/com/example/saleapp/repository/ProductsRepository.java) · [AuctionIntegrationTest.java](../src/test/java/com/example/saleapp/AuctionIntegrationTest.java)

## 2. 자동입찰 응답 가격과 저장된 가격이 달랐다

- **현상** — 낮은 도전가로 경쟁이 발생한 경우 계산 결과를 응답으로만 반환했습니다. 차순위 한도에 최소 증가액을 더한 값이 승자의 한도를 초과하는 경우도 있었습니다.
- **원인** — 이긴 경우와 진 경우에 가격 계산·저장이 서로 다른 분기에 흩어졌고, 최고 입찰자 조회에 동가 순서가 없었습니다.
- **해결** — 첫 입찰의 현재가는 시작가로 유지합니다. 두 명 이상이면 최고 한도와 차순위 한도에 증가액을 더한 값 중 작은 값을 선택하고 시작가를 하한으로 둡니다. 같은 값이 응답과 상품에 반영됩니다. 동가인 경우 해당 최대가에 먼저 도달한 입찰자를 유지하도록 변경 순번을 저장합니다. 경매 종료 시각, 최소 입찰가 및 즉시 구매가 경계도 서버에서 검사합니다.
- **검증** — 시작가 1,000원, 증가액 100원, 최고 한도 2,000원, 차순위 1,950원의 경우 **현재가 2,000원**을 응답과 DB에서 확인했습니다. 이후 도전자가 2,050원으로 올렸을 때 낙찰 후보가 바뀌고 가격은 **2,050원**에 머뭅니다. 먼저 참여한 사람이 나중에 동가로 올려도 이미 같은 최대가에 도달한 참여자가 선두를 유지하는 테스트도 있습니다.

관련 코드 [BidProxyRepository.java](../src/main/java/com/example/saleapp/repository/BidProxyRepository.java) · [AuctionIntegrationTest.java](../src/test/java/com/example/saleapp/AuctionIntegrationTest.java)

## 3. 채팅방과 인증 경로의 접근 제어가 달랐다

- **현상** — 채팅 이력과 STOMP 목적지는 방별 구매자·판매자를 공통 기준으로 검사하지 않았습니다. 리프레시 토큰은 일반 HTTP 인증에도 쓰였습니다.
- **해결** — HTTP 채팅 화면, 이력, STOMP 구독·전송에 거래 당사자 확인을 적용했습니다. 브라우저는 HttpOnly 액세스 쿠키로 화면을 열고, 상태 변경 요청에는 CSRF 토큰을 제출합니다. 리프레시 쿠키는 갱신·로그아웃에 쓰며 DB에는 토큰 해시를 저장합니다. 갱신은 동일한 이전 해시에 대해 한 요청만 성공하도록 원자적으로 처리합니다.
- **검증** — 거래 당사자의 이력 접근, 제3자의 접근 거절, STOMP 목적지별 정책, 만료된 인증 거절을 테스트했습니다. 실제 로컬 WebSocket에서 참여자가 저장된 메시지를 받고 리프레시 쿠키 없이 로그아웃 API를 호출해도 연결이 종료되는 것을 확인했습니다. 브라우저가 사용하는 로그인 페이지의 CSRF 쿠키·헤더 조합과 갱신·로그아웃도 HTTP 통합 테스트에 포함했습니다.
- **적용 범위** — 액세스 쿠키 수명은 **900초**, 리프레시 쿠키 수명은 **604,800초**입니다. 새 토큰 형식 적용 시 기존 세션은 다시 로그인해야 합니다. 로그아웃 시 현재 인스턴스의 채팅 연결을 정리합니다. 여러 인스턴스 간 세션 종료 전파는 별도 설계가 필요합니다. 외부 클라이언트가 별도로 보관한 액세스 토큰은 만료 시각까지 유효합니다.

관련 코드 [SecurityConfig.java](../src/main/java/com/example/saleapp/config/SecurityConfig.java) · [ChatSocketSessions.java](../src/main/java/com/example/saleapp/config/ChatSocketSessions.java) · [AccountIntegrationTest.java](../src/test/java/com/example/saleapp/AccountIntegrationTest.java) · [ChatSocketIntegrationTest.java](../src/test/java/com/example/saleapp/ChatSocketIntegrationTest.java)

## 4. 이미지 등록 실패 후 상품이나 파일이 남았다

- **현상** — 상품을 먼저 저장하고 이미지를 하나씩 저장해, 중간 파일이 손상되면 상품과 앞선 파일이 남을 수 있었습니다.
- **해결** — 이미지 내용과 픽셀 수를 검사하고 임시 위치에서 JPEG 썸네일로 변환합니다. 유효한 파일의 메타데이터만 저장합니다. DB 트랜잭션이 롤백되면 이미 옮긴 파일도 정리합니다. 기존 업로드의 원본은 웹 공개 경로에서 제외했고, 상품 제목은 브라우저에서 일반 텍스트로 표시합니다.
- **검증** — 정상 이미지 1장으로 공개 썸네일 **1개**만 생성됩니다. 두 번째 이미지가 손상된 경우와 발행 후 트랜잭션을 강제로 롤백한 경우 모두 새 상품·이미지 행·파일이 **0개** 남았습니다.
- **적용 범위** — 파일시스템과 DB의 단일 커밋은 제공하지 않습니다. 파일 이동 직후 프로세스가 강제 종료되는 경우에는 DB와 저장 경로를 대조해 고아 디렉터리를 정리할 수 있습니다.

관련 코드 [ProductImageStorage.java](../src/main/java/com/example/saleapp/service/ProductImageStorage.java) · [UploadIntegrationTest.java](../src/test/java/com/example/saleapp/UploadIntegrationTest.java)

## 5. 상품 목록과 채팅 이력을 한 번에 읽었다

- **해결** — 상품 목록은 DB에서 필터·정렬한 뒤 페이지를 가져오고, 필요한 대표 이미지만 일괄 조회합니다. 채팅 이력은 메시지 ID 커서로 나눠 읽습니다. 채팅방 목록의 최신 메시지와 대표 이미지도 방마다 조회하는 방식에서 일괄 조회로 바꿨습니다.
- **검증** — 같은 카테고리의 상품 **52개**로 기본 응답 **24개**, 최대 페이지 크기 **50개**, 가격순 페이지 경계를 확인했습니다. 채팅 메시지 **52개**는 **50개와 2개**로 나뉘고 반환 순서와 커서가 일치했습니다. 페이지 수치는 응답 건수이고 전체 SQL 쿼리 수나 지연시간 개선율은 측정하지 않았습니다.

관련 코드 [CatalogIntegrationTest.java](../src/test/java/com/example/saleapp/CatalogIntegrationTest.java) · [AccountIntegrationTest.java](../src/test/java/com/example/saleapp/AccountIntegrationTest.java)

## 6. 브라우저에서 로그인 뒤 상품 등록이 403으로 실패했다

- **현상** — 실제 브라우저에서 로그인까지 성공했지만, 상품 등록 요청에는 CSRF 헤더만 있고 대응하는 쿠키가 없어서 403이 반환됐습니다.
- **원인** — JWT 인증이 매 요청마다 새 세션 인증으로 감지됐습니다. 로그인 후 CSS·JavaScript 요청도 CSRF 쿠키를 지웠습니다.
- **해결** — JWT 인증은 요청 단위로 유지하고, 암묵적 세션 인증 전략과 요청 캐시를 사용하지 않도록 설정했습니다. 쿠키를 비교하는 CSRF 검증은 계속 적용합니다.
- **검증** — 로그인한 사용자의 CSS 응답이 CSRF 쿠키를 삭제하지 않는 회귀 테스트를 추가했습니다. 수정 전 실패했고 수정 후 통과했습니다.

관련 코드 [SecurityConfig.java](../src/main/java/com/example/saleapp/config/SecurityConfig.java) · [AccountIntegrationTest.java](../src/test/java/com/example/saleapp/AccountIntegrationTest.java) · [Spring Security 세션 관리 문서](https://docs.spring.io/spring-security/reference/servlet/authentication/session-management.html)

## 검증 기록

| 항목 | 결과 및 조건 |
| --- | --- |
| 변경 전 경매 회귀 테스트 | H2에서 신규 경매 테스트 **14건 실패**. 해당 14건은 변경 후 통과 |
| 경매 동시 구매 | MySQL 8.0.46에서 **10회 중 10회** 낙찰자 1명, 방 1개 |
| 전체 테스트와 실행 JAR | JDK 21과 Gradle 8.14.3에서 `test bootJar` 실행. 로컬 H2 **34건**, 격리 MySQL **34건** 모두 통과. 실행 JAR 생성 |
| 마이그레이션 | 빈 MySQL에 Flyway 적용과 JPA 스키마 검증. 기존 형태의 가상 데이터베이스에서 V1 기준선 채택 후 V2 적용, 채팅방 보존 및 입찰 순번 초기화 확인 |
| 실제 Chrome과 MySQL 동작 | 별도 계정 3개 로그인, 이미지 상품 등록과 썸네일 표시, 자동입찰 현재가 10,000원 → 20,000원, 새로고침 후 20,000원 유지, 25,000원 즉시 구매, STOMP 양방향 메시지 2건 및 이력 2건 확인 |
| 운영 적용 | 실제 운영 DB 및 운영 트래픽에 대한 검증은 수행하지 않음 |

## 면접에서 설명할 문장

> 같은 중고 상품에 두 구매 요청이 동시에 들어오면 낙찰 정보와 채팅방이 어긋날 수 있었습니다. 상품 행 잠금 안에서 경매 상태를 다시 확인하고, 낙찰 확정과 채팅방 생성을 하나의 트랜잭션으로 묶었습니다. MySQL에서 두 요청을 동시에 보내는 테스트를 10회 실행해 매번 성공 1건, 충돌 1건, 채팅방 1개를 확인했습니다. 동일 상품 경쟁을 직렬화했기 때문에 운영에서는 잠금 대기와 응답 지연을 따로 관측할 수 있습니다.

포트폴리오에는 실제 운영 수치 대신 **테스트 환경, 입력 조건, 관측 결과**를 함께 적을 수 있습니다. 성능 수치를 추가로 측정한다면 같은 데이터량과 동시 요청 수로 p50·p95, 실패율 및 MySQL 잠금 대기를 기록하면 됩니다.
