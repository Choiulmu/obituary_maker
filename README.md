# obituary_maker

몇 가지 정보만 입력하면 **온라인 부고장을 만들고 링크를 카카오톡 등으로 공유**할 수 있는 서비스다.
회원가입·로그인 없이 쓰고, 어르신도 혼자 만들 수 있도록 큰 글씨와 단순한 흐름(입력 → 미리보기 → 생성)을 지킨다.

```text
입력 → 미리보기 → 생성 → 링크 공유
                    ├─ S3에 부고장 HTML 발행
                    ├─ Google Sheets에 기록
                    └─ 운영자 알림(로그)
```

| 항목 | 내용 |
|---|---|
| Backend | Java 17, Spring Boot 3.4 |
| Frontend | Thymeleaf + HTML/CSS (SPA 없음) |
| 저장소 | Google Sheets (MVP 한정, DB 없음) |
| 발행 | S3 정적 호스팅 |
| 설정 | 로컬은 `.env`, 실서버는 AWS Parameter Store |

문서: [API 목록](docs/api.md) · [데이터 양식 & 아키텍처](docs/architecture.md) · [테스트 가이드](docs/testing.md)

---

## 로컬 실행

### 1. 준비물

- JDK 17 (Gradle toolchain이 17로 고정되어 있다)
- 포트 8080
- Gradle은 따로 설치하지 않는다. `./gradlew`를 쓴다.

### 2. `.env` 만들기

값은 프로젝트 루트의 `.env`에서 읽는다. **properties 형식**이고 커밋하지 않는다.

```bash
cp .env.example .env
```

| 값 | 필수 | 없으면 | 기본값 |
|---|---|---|---|
| `google-credentials` | **O** | **서버가 뜨지 않는다** | 빈 값 |
| `spreadsheet-id` | X | 시트 저장/조회 실패 (기동은 됨) | 빈 값 |
| `admin-token` | X | `/admin/**`이 항상 401 | 빈 값 |
| `s3-bucket` | X | S3 업로드 실패 (기동은 됨) | `obituary-local` |
| `public-base-url` | X | - | `http://localhost:8080` |

`google-credentials`에는 서비스 계정 **키 파일 경로**를 적는다. 여러 줄 JSON은 properties 파일에 넣을 수 없기 때문이다.

```properties
google-credentials=/Users/me/.obituary-local/sa.json
spreadsheet-id=1AbC...
admin-token=local-token
```

화면만 확인할 거면 진짜 키가 필요 없다. 가짜 키 만드는 방법은 [테스트 가이드 2번](docs/testing.md)에 있다.

### 3. `google-credentials` 키 파일 받기

시트에 실제로 저장·조회하려면 GCP 서비스 계정 키가 필요하다. [Google Cloud Console](https://console.cloud.google.com)에서 한 번만 만들면 된다.

1. 프로젝트를 만들거나 기존 프로젝트를 고른다.
2. **APIs & Services > Library**에서 `Google Sheets API`를 켠다. (Enable)
3. **APIs & Services > Credentials > Create credentials > Service account**로 서비스 계정을 만든다. 역할(Role)은 주지 않아도 된다. 시트 권한은 4번에서 준다.
4. 만들어진 서비스 계정의 이메일(`...@....iam.gserviceaccount.com`)을 복사해서, 사용할 **스프레드시트의 공유 버튼**에 붙여넣고 **편집자**로 추가한다. 이걸 빼먹으면 `403 The caller does not have permission`이 난다.
5. 서비스 계정 상세 화면의 **Keys > Add key > Create new key > JSON**을 눌러 키 파일을 내려받는다.
6. 내려받은 파일을 저장소 밖(예: `~/.obituary-local/sa.json`)에 두고, 그 경로를 `.env`의 `google-credentials`에 적는다. **키 파일은 커밋하지 않는다.**

`spreadsheet-id`는 시트 URL `docs.google.com/spreadsheets/d/`**`여기`**`/edit` 부분이다.

### 4. 실행 — 명령어

```bash
./gradlew bootRun     # 서버 (http://localhost:8080)
./gradlew test        # 테스트
```

`Started ObituaryMakerApplication in ...` 이 찍히면 기동 완료다.

값을 잠깐만 바꿔 볼 때는 `.env` 대신 인자로 덮어써도 된다.

```bash
./gradlew bootRun --args='--s3-bucket=내-테스트-버킷'
```

### 5. 실행 — IntelliJ

1. **Open**으로 프로젝트 루트를 열면 Gradle 프로젝트로 임포트된다.
2. `File > Project Structure > Project`에서 SDK를 17로 맞춘다.
3. `src/main/java/com/example/obituarymaker/ObituaryMakerApplication.java`를 열고 클래스 옆 ▶ 를 눌러 실행한다.
4. 실행 설정은 따로 만들 필요가 없다. `.env`는 **작업 디렉터리 기준 상대 경로**로 읽으므로, `Run/Debug Configurations`의 **Working directory**가 프로젝트 루트인지만 확인한다. (기본값이 루트다)
5. 값을 임시로 바꾸려면 같은 창의 **Program arguments**에 `--s3-bucket=내-테스트-버킷` 처럼 넣는다.

테스트는 `src/test`에서 클래스나 디렉터리를 우클릭 → **Run Tests**.

### 6. 자주 만나는 오류

| 로그 | 조치 |
|---|---|
| `IllegalStateException: google-credentials 값이 없습니다.` | `.env`에 키 파일 경로를 넣는다 |
| `403 The caller does not have permission` | 스프레드시트를 서비스 계정 이메일과 공유하지 않았다 (3번 4단계) |
| `NoSuchBucketException` | 기본 버킷 `obituary-local`이 없다. `s3-bucket`을 실제 버킷으로 바꾼다 |
| `Unable to load credentials` | `aws configure` 또는 `AWS_PROFILE` 설정 |
| `/admin/**`이 계속 401 | `admin-token`이 비어 있으면 무조건 401이다 |

나머지는 [테스트 가이드 7번](docs/testing.md)에 정리해 두었다.

---

## 실서버

프로필이 `local`이 아니면 `.env`를 보지 않고 **Parameter Store `/obituary/`** 에서만 값을 읽는다. 값이 없으면 기동에 실패한다.

```bash
SPRING_PROFILES_ACTIVE=prod java -jar build/libs/obituary-maker-0.0.1-SNAPSHOT.jar
```

AWS 액세스 키만 서버의 `~/.aws/credentials`에 둔다. Lightsail 인스턴스에는 IAM 역할을 붙일 수 없고, 키가 있어야 Parameter Store를 읽을 수 있어서 이것만은 파일로 간다. 자세한 내용은 [아키텍처 문서의 설정 값](docs/architecture.md) 참고.
