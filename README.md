# INFORM Backend

> 인하대학교 학생들을 위한 **정보 통합 플랫폼** 백엔드 서버입니다.  
> 학교 공지사항·동아리 소식을 중앙 집중화하여 제공하며, 캘린더·북마크·알림 기능을 포함합니다.

---

## 🛠️ 기술 스택

### Core
- **Java 21** - 언어
- **Spring Boot 4.0.2** - 애플리케이션 프레임워크
- **Gradle** - 빌드 도구

### 보안 & 인증
- **Spring Security** - Stateless JWT 기반 인증
- **Google OAuth2** - ID Token 검증 (인하대 도메인 제한)
- **JJWT 0.12.6** - JWT 발급 및 파싱

### 데이터
- **Spring Data JPA** + **MariaDB** - 영속성 계층
- **QueryDSL 5.1.0 (Jakarta)** - 동적 필터링 및 복잡 쿼리
- **Redis** - Refresh Token 저장 (TTL 14일), 응답 캐싱

### 인프라 & 개발 도구
- **Docker** - 컨테이너 배포
- **GitHub Actions** - CI/CD 자동화
- **GCP VM** - 운영 서버
- **Lombok** - 보일러플레이트 제거
- **H2 (테스트 전용)** - 단위 테스트용 인메모리 DB

---

## 📁 프로젝트 구조

```
src/
├── main/java/today/inform/inform_backend/
│   │
│   ├── common/
│   │   ├── exception/
│   │   │   ├── BusinessException.java       # 비즈니스 로직 예외 (ErrorCode를 감싸는 런타임 예외)
│   │   │   ├── ErrorCode.java               # 에러 코드 열거형 (HTTP 상태, 코드, 메시지 정의)
│   │   │   └── GlobalExceptionHandler.java  # @RestControllerAdvice 전역 예외 처리기
│   │   └── response/
│   │       └── ApiResponse.java             # 공통 응답 래퍼 { success, data, error }
│   │
│   ├── config/
│   │   ├── JpaAuditConfig.java              # @EnableJpaAuditing - 생성/수정 시간 자동 기록 활성화
│   │   ├── QueryDslConfig.java              # JPAQueryFactory 빈 등록
│   │   └── SecurityConfig.java             # Spring Security 설정 (JWT 필터 체인, CORS, 인증 경로 정의)
│   │
│   ├── controller/
│   │   ├── AuthController.java              # POST /auth/login/google, /auth/refresh, /auth/logout
│   │   ├── BookmarkController.java          # POST·GET·DELETE /bookmarks
│   │   ├── CalendarController.java          # GET /calendar/notices
│   │   ├── ClubArticleController.java       # GET /club_articles, /club_articles/{id}
│   │   ├── CommonController.java            # GET /vendors, /categories
│   │   ├── NotificationController.java      # GET·PATCH /notifications
│   │   ├── SchoolArticleController.java     # GET /school_articles, /school_articles/{id}, /popular
│   │   └── UserController.java              # GET·PATCH·DELETE /users/me
│   │
│   ├── dto/
│   │   ├── BookmarkRequest.java             # 북마크 토글 요청 (article_type, article_id)
│   │   ├── CalendarDailyListResponse.java   # 캘린더 일정 응답
│   │   ├── CategoryListResponse.java        # 카테고리 목록 응답
│   │   ├── ClubArticleDetailResponse.java   # 동아리 공지 상세 응답
│   │   ├── ClubArticleListResponse.java     # 동아리 공지 목록 응답 (page_info 포함)
│   │   ├── ClubArticleResponse.java         # 동아리 공지 개별 항목 응답
│   │   ├── GoogleUserInfo.java              # Google ID Token 검증 후 추출한 사용자 정보
│   │   ├── LoginRequest.java                # 구글 로그인 요청 (id_token)
│   │   ├── LoginResponse.java               # 로그인 응답 (access_token, refresh_token, user_info)
│   │   ├── NotificationResponse.java        # 알림 응답 (notification_id, title, message, is_read)
│   │   ├── SchoolArticleDetailResponse.java # 학교 공지 상세 응답
│   │   ├── SchoolArticleListResponse.java   # 학교 공지 목록 응답 (page_info 포함)
│   │   ├── SchoolArticleResponse.java       # 학교 공지 개별 항목 응답
│   │   ├── TokenRefreshRequest.java         # 토큰 재발급 요청 (refresh_token)
│   │   ├── TokenRefreshResponse.java        # 토큰 재발급 응답 (access_token, refresh_token)
│   │   ├── UserUpdateRequest.java           # 학과 수정 요청 (major_id)
│   │   └── VendorListResponse.java          # 제공처 응답 (공용 DTO - 여러 응답에서 재사용)
│   │
│   ├── entity/
│   │   ├── Attachment.java                  # 첨부파일 (article_type, article_id, url)
│   │   ├── BaseCreatedTimeEntity.java       # created_at 자동 기록 베이스 엔티티
│   │   ├── BaseTimeEntity.java              # created_at + updated_at 자동 기록 베이스 엔티티
│   │   ├── Bookmark.java                    # 북마크 (user, article_type, article_id)
│   │   ├── Category.java                    # 공지 카테고리 (장학, 대회·공모전 등)
│   │   ├── ClubArticle.java                 # 동아리 공지사항
│   │   ├── Notification.java                # 알림 (user cascade delete, is_read)
│   │   ├── RefreshToken.java                # Redis 저장 Refresh Token (email 키, TTL 14일)
│   │   ├── SchoolArticle.java               # 학교 공지사항
│   │   ├── SchoolArticleVendor.java         # 학교 공지 ↔ 제공처 N:M 매핑
│   │   ├── SocialType.java                  # 소셜 로그인 타입 열거형 (GOOGLE)
│   │   ├── User.java                        # 사용자 (email, name, major_id)
│   │   ├── Vendor.java                      # 제공처 (학과·동아리, vendor_type)
│   │   └── VendorType.java                  # 제공처 타입 열거형 (SCHOOL, CLUB)
│   │
│   ├── repository/
│   │   ├── AttachmentRepository.java        # 첨부파일 조회
│   │   ├── BookmarkRepository.java          # 북마크 조회·삭제
│   │   ├── CategoryRepository.java          # 카테고리 목록 조회
│   │   ├── ClubArticleRepository.java       # 동아리 공지 기본 CRUD
│   │   ├── ClubArticleRepositoryCustom.java # 동아리 공지 QueryDSL 인터페이스
│   │   ├── ClubArticleRepositoryImpl.java   # 동아리 공지 QueryDSL 구현체 (키워드 검색, 필터)
│   │   ├── NotificationRepository.java      # 알림 조회·읽음 처리·삭제
│   │   ├── RefreshTokenRepository.java      # Redis Refresh Token CRUD
│   │   ├── SchoolArticleRepository.java     # 학교 공지 기본 CRUD
│   │   ├── SchoolArticleRepositoryCustom.java  # 학교 공지 QueryDSL 인터페이스
│   │   ├── SchoolArticleRepositoryImpl.java    # 학교 공지 QueryDSL 구현체 (다중 필터, 정렬, 날짜 Overlap)
│   │   ├── SchoolArticleVendorRepository.java  # 공지-제공처 매핑 조회
│   │   ├── UserRepository.java              # 사용자 조회·저장
│   │   └── VendorRepository.java            # 제공처 목록 조회
│   │
│   ├── scheduler/
│   │   └── NotificationScheduler.java       # 마감 D-1 알림 생성(오전 9시), 30일 경과 알림 삭제(오전 4시)
│   │
│   └── service/
│       ├── BookmarkService.java             # 북마크 토글, 목록 조회, 전체 삭제
│       ├── CalendarService.java             # 월간 일정 조회 (날짜 Overlap 쿼리)
│       ├── ClubArticleService.java          # 동아리 공지 목록·상세 조회
│       ├── CommonService.java               # 제공처·카테고리 목록 조회 (캐싱 적용)
│       ├── NotificationService.java         # 알림 조회, 읽음 처리
│       └── SchoolArticleService.java        # 학교 공지 목록·상세·인기 공지 조회
│
└── test/                                    # JUnit5 단위 테스트 (H2 in-memory, spring.profiles.active=test)
```

---

## 🌐 API 구조

> **Base URL**: `https://api.inform.today/api/v1`  
> **공통 응답 규격**: 모든 API는 `{ success, data, error }` 형태의 `ApiResponse<T>`로 응답합니다.  
> **인증**: `Authorization: Bearer {access_token}` 헤더 사용

### 인증 (Auth)

| Method | Endpoint | 설명 | 인증 |
|--------|----------|------|------|
| `POST` | `/auth/login/google` | 구글 OAuth2 로그인 및 JWT 발급 | ❌ |
| `POST` | `/auth/refresh` | Access/Refresh Token 재발급 (RTR 방식) | ❌ |
| `POST` | `/auth/logout` | 로그아웃 (Redis RT 즉시 삭제) | ✅ |

### 사용자 (User)

| Method | Endpoint | 설명 | 인증 |
|--------|----------|------|------|
| `GET` | `/users/me` | 내 정보 조회 (학과 상세 포함) | ✅ |
| `PATCH` | `/users/me/major` | 소속 학과 수정 | ✅ |
| `DELETE` | `/users/me` | 회원 탈퇴 (연관 데이터 일괄 삭제) | ✅ |

### 학교 공지사항 (School Articles)

| Method | Endpoint | 설명 | 인증 |
|--------|----------|------|------|
| `GET` | `/school_articles` | 목록 조회 (카테고리·제공처 다중 필터, 키워드 검색, 페이징) | 선택 |
| `GET` | `/school_articles/{id}` | 상세 조회 | 선택 |
| `GET` | `/school_articles/popular` | 인기 공지 조회 (북마크 수 기준) | 선택 |

> 정렬 우선순위: 진행 중(`OPEN`) → 마감 임박(`ENDING_SOON`) → 시작 예정(`UPCOMING`)

### 동아리 공지사항 (Club Articles)

| Method | Endpoint | 설명 | 인증 |
|--------|----------|------|------|
| `GET` | `/club_articles` | 목록 조회 (동아리 필터, 키워드 검색, 페이징) | ❌ |
| `GET` | `/club_articles/{id}` | 상세 조회 | ❌ |

### 캘린더 (Calendar)

| Method | Endpoint | 설명 | 인증 |
|--------|----------|------|------|
| `GET` | `/calendar/notices` | 월간 일정 조회 (`year`, `month`, 카테고리 필터, 내 북마크만 보기) | 선택 |

### 북마크 (Bookmark)

| Method | Endpoint | 설명 | 인증 |
|--------|----------|------|------|
| `POST` | `/bookmarks` | 북마크 토글 (등록/해제) | ✅ |
| `GET` | `/bookmarks/school_articles` | 북마크한 학교 공지 목록 조회 | ✅ |
| `DELETE` | `/bookmarks` | 북마크 전체 삭제 | ✅ |

### 알림 (Notification)

| Method | Endpoint | 설명 | 인증 |
|--------|----------|------|------|
| `GET` | `/notifications` | 알림 목록 조회 (최신순) | ✅ |
| `GET` | `/notifications/unread-count` | 읽지 않은 알림 개수 조회 | ✅ |
| `PATCH` | `/notifications/{id}/read` | 개별 알림 읽음 처리 | ✅ |
| `PATCH` | `/notifications/read-all` | 모든 알림 일괄 읽음 처리 | ✅ |

### 공통 (Common)

| Method | Endpoint | 설명 | 인증 |
|--------|----------|------|------|
| `GET` | `/vendors` | 제공처 목록 조회 (`type=SCHOOL\|CLUB` 필터 지원, 캐싱 적용) | ❌ |
| `GET` | `/categories` | 카테고리 목록 조회 (캐싱 적용) | ❌ |

---

## 🚦 시작하기

### 사전 요구사항

- Java 21 이상
- MariaDB
- Redis

### 설치

```bash
# 저장소 클론
git clone https://github.com/Team-Alimi/IN-FORM_Backend.git

# 디렉토리 이동
cd inform-backend

# 설정 파일 생성
cp src/main/resources/application.yaml.example src/main/resources/application.yaml
# application.yaml에 DB, Redis, JWT, Google OAuth2 정보 입력
```

### 개발 서버 실행

```bash
./gradlew bootRun
```

서버가 `http://localhost:8080`에서 실행됩니다.

### 테스트

```bash
./gradlew test
```

### 빌드

```bash
./gradlew clean build
```

빌드된 JAR 파일은 `build/libs/` 폴더에 생성됩니다.

### Docker 실행

```bash
docker build -t inform-backend .
docker run -d -p 8080:8080 --name inform-backend inform-backend
```

---

## 🔄 CI/CD

`main` 브랜치에 Push 또는 PR 시 GitHub Actions가 자동으로 실행됩니다.

```
Push → GitHub Actions
  1. JDK 21 설정
  2. application.yaml 주입 (GitHub Secrets)
  3. ./gradlew build (테스트 포함)
  4. GCP VM으로 JAR + Dockerfile 전송 (SCP)
  5. GCP VM에서 Docker 이미지 빌드 & 컨테이너 재시작
```

---

## 🎯 개발 가이드

### 네이밍 규칙

- 클래스: `PascalCase`
- 메서드 / 변수: `camelCase`
- JSON 응답 필드: `snake_case` (전역 Jackson 설정 적용)
- 상수: `UPPER_SNAKE_CASE`

### 아키텍처 컨벤션

- **응답 규격**: 모든 API는 `ApiResponse<T>` 공통 래퍼로 통일
- **예외 처리**: `ErrorCode` + `BusinessException` 기반 중앙 집중형 처리
- **Fetch Policy**: 모든 `@ManyToOne`은 `LAZY` 로딩 원칙
- **TDD**: 기능 구현 시 단위 테스트 작성 후 통과 확인

---


## 👥 Contact

team.alimi.inform@gmail.com
