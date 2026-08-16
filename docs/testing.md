# 개발자 테스트 가이드

로컬에서 서버를 띄우고 화면과 API를 확인하는 방법. 아래 내용은 macOS + Java 17에서 실제로 실행해 확인한 결과다.

---

## 1. 준비물

| 항목 | 값 |
|---|---|
| JDK | 17 (Gradle toolchain이 17로 고정) |
| 빌드 | `./gradlew` (Gradle Wrapper, 별도 설치 불필요) |
| 포트 | 8080 |

---

## 2. 서버를 띄우기 위한 최소한의 값

값은 프로젝트 루트의 **`.env`**(properties 형식)에 적는다. `.env.example`을 복사해서 쓰면 된다. `.env`는 커밋하지 않는다.

```bash
cp .env.example .env
```

기본 프로필이 `local`이라 `.env`는 아무 설정 없이 읽힌다. 실서버는 `SPRING_PROFILES_ACTIVE`로 다른 프로필을 주고, 그때는 `.env`를 보지 않고 Parameter Store(`/obituary/`)에서만 읽는다.

**필수는 `google-credentials` 하나다.** 나머지는 `application.yml`에 로컬용 기본값이 있어 없어도 뜬다.

| 값 | 필수 | 없으면 | 기본값 |
|---|---|---|---|
| `google-credentials` | **O** | **서버가 뜨지 않는다** | 빈 값 |
| `spreadsheet-id` | X | 시트 저장/조회 실패 (기동은 됨) | 빈 값 |
| `admin-token` | X | `/admin/**`이 항상 401 | 빈 값 |
| `s3-bucket` | X | S3 업로드 실패 (기동은 됨) | `obituary-local` |
| `public-base-url` | X | - | `http://localhost:8080` |
| `AWS_REGION` (환경 변수) | X | - | `ap-northeast-2` |

`google-credentials`에는 **키 파일 경로**를 적는다. properties 파일에는 여러 줄 JSON을 넣을 수 없기 때문이다. (값이 `{`로 시작하면 JSON 본문으로 읽으므로, 예전처럼 `GOOGLE_CREDENTIALS` 환경 변수에 JSON 본문을 넣어도 그대로 동작한다.)

비어 있으면 `Sheets` 빈을 만들 때 아래처럼 죽는다.

```
Caused by: java.lang.IllegalStateException: google-credentials 값이 없습니다. 운영은 Parameter Store(/obituary/google-credentials), 로컬은 .env를 확인한다.
```

### 화면만 볼 거면 — 가짜 서비스 계정 키

부고장 생성(S3·Sheets)까지 갈 게 아니라면 진짜 키가 필요 없다.
`GoogleCredentials`가 JSON 형식과 개인키 파싱까지만 하므로, 아래처럼 만든 가짜 키로도 서버가 뜬다.

```bash
mkdir -p ~/.obituary-local && cd ~/.obituary-local
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out fake.pem
python3 - <<'PY'
import json
key = open('fake.pem').read()
json.dump({
    "type": "service_account",
    "project_id": "local-test",
    "private_key_id": "local",
    "private_key": key,
    "client_email": "local@local-test.iam.gserviceaccount.com",
    "client_id": "0",
    "token_uri": "https://oauth2.googleapis.com/token",
}, open('fake-sa.json', 'w'))
PY
```

키 파일은 저장소 안에 두지 않는다. (`.gitignore`에 없다)

### 생성까지 테스트하려면 — 진짜 값

| 값 | 받는 곳 |
|---|---|
| `google-credentials` | GCP 서비스 계정 키 파일을 받아 둔 경로. 해당 계정 이메일에 스프레드시트 **편집** 권한을 준다 |
| `spreadsheet-id` | 스프레드시트 URL의 `/d/` 뒤 문자열. 시트(탭) 이름은 `obituaries`, 1행은 헤더 |
| S3 버킷 | 쓰기 권한이 있는 버킷. `s3-bucket`을 그 이름으로 덮어쓴다 |
| AWS 자격 증명 | `aws configure` 또는 `AWS_PROFILE`. S3 `PutObject` 권한 필요 |

---

## 3. 실행

`.env`에 값을 채웠으면 그냥 띄우면 된다.

```bash
# 테스트만
./gradlew test

# 서버
./gradlew bootRun
```

`.env` 예시 — 화면만 볼 때는 가짜 키 경로만 있으면 된다.

```properties
google-credentials=/Users/me/.obituary-local/fake-sa.json
```

생성까지 확인할 때:

```properties
google-credentials=/Users/me/.obituary-local/sa.json
spreadsheet-id=1AbC...
admin-token=local-token
s3-bucket=내-테스트-버킷
```

`Started ObituaryMakerApplication in ...` 이 찍히면 기동 완료다. → http://localhost:8080

---

## 4. 로컬에서 되는 것 / 안 되는 것

가짜 키 + AWS/시트 설정 없이 띄웠을 때 기준이다.

| 기능 | 가짜 키만 | 필요한 것 |
|---|---|---|
| `GET /` 랜딩 | O | - |
| `GET /obituaries/new` 입력 폼 | O | - |
| `POST /obituaries/preview` 미리보기 | O | - |
| 입력 검증 / 에러 메시지 | O | - |
| `GET /obituaries/{id}/complete` 완료 화면 | O | - (링크는 ID로 조합만 한다) |
| `POST /obituaries` 부고장 생성 | X | S3 버킷 + 시트 |
| 발행된 부고장 페이지 (`view.html`) | X | S3 (앱이 서빙하지 않는다) |
| `PATCH /admin/obituaries/{id}` | X | `admin-token` + 시트 + S3 |

발행 페이지는 앱이 아니라 S3가 응답하므로 로컬에 URL이 없다.
`view.html`의 모양은 미리보기 화면(`preview.html`)으로 대신 확인한다.

---

## 5. 화면 시나리오

모바일 화면 기준이므로 브라우저 개발자 도구의 모바일 뷰(예: iPhone SE)로 확인한다.

1. `/` → **부고장 만들기** 버튼 하나만 보이는가
2. 폼에서 아무것도 안 넣고 제출 → 어떤 항목이 왜 잘못됐는지 한글로 나오는가
3. 연락처에 `123` 입력 → `연락처를 010-1234-5678 형식으로 입력해 주세요.`
4. 별세일에 `2026-13-99` 입력 → `별세일을 다시 확인해 주세요.`
5. 이름에 `  홍길동  ` 처럼 앞뒤 공백 → 미리보기에서 공백이 잘려 있는가
6. 정상 입력 → 미리보기에 입력값이 그대로, 날짜는 `2026년 8월 14일` 형태로 나오는가
7. 선택 항목(주소·계좌)을 비워 제출 → 미리보기에서 해당 줄이 사라지는가
8. 미리보기에서 **부고장 만들기** → 완료 화면으로 이동 (S3 설정 필요)
9. 완료 화면의 **링크 복사** → 클립보드에 링크가 들어가는가 (`navigator.share`는 PC에서 복사로 넘어간다)

---

## 6. curl 확인

```bash
# 랜딩 / 폼
curl -s -o /dev/null -w "%{http_code}\n" localhost:8080/
curl -s -o /dev/null -w "%{http_code}\n" localhost:8080/obituaries/new

# 미리보기 (성공 → 200 + preview.html)
curl -s -X POST localhost:8080/obituaries/preview \
  -d "name=홍길동" -d "deathDate=2026-08-14" \
  -d "funeralHome=서울추모공원" -d "room=3호실" \
  -d "departureDate=2026-08-16" \
  -d "mournerName=홍철수" -d "mournerPhone=010-1234-5678"

# 검증 실패 (200 + form.html + 에러 문구)
curl -s -X POST localhost:8080/obituaries/preview -d "name=" -d "mournerPhone=123" \
  | grep -o "[가-힣 .0-9-]*주세요\."

# 생성 (성공 → 302, 실패 → 200 + 폼)
curl -s -o /dev/null -w "%{http_code} %{redirect_url}\n" -X POST localhost:8080/obituaries \
  -d "name=홍길동" -d "deathDate=2026-08-14" \
  -d "funeralHome=서울추모공원" -d "room=3호실" \
  -d "departureDate=2026-08-16" \
  -d "mournerName=홍철수" -d "mournerPhone=010-1234-5678"

# 운영자 수정
curl -X PATCH localhost:8080/admin/obituaries/{id} \
  -H "X-Admin-Token: local-token" -d "room=5호실"
```

`POST /obituaries`가 성공하면 서버 로그에 운영자 알림이 찍히고, 여기에 수정용 curl 명령이 들어 있다.

### 운영자 API 응답

| 상황 | 코드 | 본문 |
|---|---|---|
| 정상 | 200 | `수정했습니다.` |
| 토큰 없음/불일치, `admin-token` 미설정 | 401 | `권한이 없습니다.` |
| 시트에 없는 ID | 404 | `부고장을 찾을 수 없습니다.` |
| 값 형식 오류, 필수 항목을 빈 값으로 | 400 | 위반한 검증 메시지 |
| 목록에 없는 필드명 (`id`, 오타 등) | 400 | `고칠 수 없는 항목입니다: xxx` |

---

## 7. 자주 만나는 오류

| 로그 | 원인 | 조치 |
|---|---|---|
| `IllegalStateException: google-credentials 값이 없습니다.` | 기동 실패. `.env`에 `google-credentials` 없음 | 2번의 가짜/진짜 키 설정 |
| `NoSuchBucketException: The specified bucket does not exist` | 기본값 `obituary-local` 버킷이 없음 | `--s3-bucket=` 로 실제 버킷 지정 |
| `Unable to load credentials` / `SdkClientException` | AWS 자격 증명 없음 | `aws configure` 또는 `AWS_PROFILE` |
| `Requested entity was not found` (Sheets) | `spreadsheet-id` 없음/오타 | 스프레드시트 ID 확인 |
| `The caller does not have permission` (Sheets) | 서비스 계정에 시트 권한 없음 | 시트 공유에 서비스 계정 이메일을 편집자로 추가 |
| `Unable to parse range: obituaries!A:M` | 시트(탭) 이름이 `obituaries`가 아님 | 탭 이름 변경 |
| 부고장 생성 시 화면에 `부고장을 만들지 못했습니다.` | S3 또는 Sheets 실패 | 서버 로그의 `부고장 생성 실패` 스택 확인 |
| `/admin/**`이 계속 401 | `admin-token`이 빈 값이면 무조건 401 | `.env`에 `admin-token` 지정 후 재기동 |

---

## 8. 자동 테스트

```bash
./gradlew test          # 리포트: build/reports/tests/test/index.html
./gradlew test --rerun  # 캐시 무시하고 다시 실행
```

| 테스트 | 확인 범위 |
|---|---|
| `ObituaryControllerTest` | 폼·미리보기·생성·완료 화면, 검증 실패 시 안내 문구, 공백 잘라내기 |
| `AdminControllerTest` | 토큰 검사(401), 없는 ID(404), 값 오류(400), 넘어온 항목만 수정 |
| `ObituaryTest` | 시트 행 A~M 변환·복원, 필드 덮어쓰기, 길이·형식 검증, 날짜 한글 표시 |
| `S3PublisherTest` | 공유 링크 형식, `{id}/index.html` 업로드 요청 |

외부(Google Sheets·S3)는 모두 목으로 대체해서 자격 증명 없이 실행된다.
