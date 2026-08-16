# 데이터 양식 & 아키텍처

MVP 기준. 데이터는 Google Sheets에 기록하고, 발행된 부고장은 S3 정적 HTML로 서빙한다.
애플리케이션은 Spring Boot 단일 서버다.
필드 이름과 예시는 [api.md](./api.md)와 동일하게 맞춘다.

---

## 1. Google Sheets 저장 양식

### 스프레드시트 구성

| 항목 | 값 |
|---|---|
| 스프레드시트 | `부고장 관리` |
| 시트(탭) | `obituaries` |
| 1행 | 헤더 (고정) |
| 2행부터 | 부고장 1건 = 1행, 생성 순서대로 append |

### 컬럼

| 열 | 컬럼명 | 필드 | 필수 | 예시 |
|---|---|---|---|---|
| A | 부고장ID | id | O | `a1b2c3d4-e5f6-4718-9abc-0123456789ab` |
| B | 생성일시 | createdAt | O | `2026-08-15 14:32:10` |
| C | 수정일시 | updatedAt | X | `2026-08-15 16:04:22` |
| D | 고인이름 | name | O | `홍길동` |
| E | 별세일 | deathDate | O | `2026-08-14` |
| F | 장례식장 | funeralHome | O | `서울추모공원` |
| G | 빈소 | room | O | `3호실` |
| H | 발인일 | departureDate | O | `2026-08-16` |
| I | 상주이름 | mournerName | O | `홍철수` |
| J | 상주연락처 | mournerPhone | O | `010-1234-5678` |
| K | 장례식장주소 | address | X | `서울시 서초구 원지동 산4-1` |
| L | 조의금계좌 | account | X | `국민 123456-01-123456 홍철수` |
| M | 부고장링크 | shareUrl | O | `https://obituary.example.com/a1b2c3d4-e5f6-4718-9abc-0123456789ab/` |

### 저장 규칙

- 날짜는 `yyyy-MM-dd`, 시각을 포함하면 `yyyy-MM-dd HH:mm` (KST) 문자열로 저장한다. 초는 쓰지 않는다.
- 예외로 생성일시·수정일시 같은 기록용 값만 `yyyy-MM-dd HH:mm:ss`로 초까지 남긴다. 같은 분에 들어온 건의 순서를 구분해야 하기 때문이다.
- 선택 항목 미입력 시 빈 문자열로 저장한다. (열 위치가 밀리지 않도록 항상 A~M 전체를 쓴다)
- 부고장링크(M)는 S3 업로드가 성공한 뒤에 기록한다. 업로드에 실패하면 시트에 행 자체를 남기지 않는다. 열리지 않는 링크가 시트에 있으면 운영자가 정상 건과 구분할 수 없다.
- 시트 값은 모두 문자열(`USER_ENTERED` 대신 `RAW`)로 기록한다. Sheets가 `010-1234-5678`을 날짜/수식으로 자동 변환하는 것을 막기 위함이다.
- 수정일시(C)는 생성 시 빈 문자열로 두고, 수정 API가 호출될 때만 채운다. 비어 있으면 한 번도 고치지 않은 건이라는 뜻이다.
- 시트를 바꾸는 것은 생성 시 append와 수정 API의 행 갱신 두 가지뿐이다. 운영자가 브라우저에서 시트를 직접 고치면 S3의 HTML과 값이 어긋난다.
- 고객이 적은 값은 공백까지 그대로 저장한다. 앞뒤 공백을 잘라내지 않는다. 시트에 남는 것과 부고장 페이지에 박히는 것이 같아야 한다. 날짜만 예외로 파싱 전에 공백을 잘라낸다.
- 고객이 넣는 값(D~L)의 길이는 [api.md의 입력 필드](./api.md#입력-필드)에 정한 만큼으로 제한한다. 공백도 한 글자로 센다. 시트 용량이 아니라 부고장 화면이 기준이다.
- 상주이름(I)과 상주연락처(J)에는 한 명만 들어간다. 이름에 공백·쉼표가 있으면 저장 전에 거른다.
- 부고장ID(A)는 랜덤 UUID다. 원래 예시는 8자였는데, 발행된 URL이 인증 없이 열리고 상주 연락처가 들어 있어 UUID를 그대로 쓴다. 손으로 칠 일이 없는 값이라 길어도 불편하지 않고, 잘라 쓰면 짧아진 만큼 겹칠 확률만 는다.

### 조회

시트를 읽는 곳은 수정 API(`PATCH /admin/obituaries/{id}`) 하나뿐이다.
A열(부고장ID)로 행을 찾으며, MVP 데이터량 기준으로는 시트 전체를 읽어 ID를 비교하는 방식으로 충분하다.

공유 링크로 들어오는 조회 트래픽은 시트를 거치지 않는다. S3가 응답하므로 Sheets API 호출량·지연과 무관하다.

---

## 2. 아키텍처

```text
사용자
  ├─ 작성자 (모바일 브라우저)  ──▶  Spring Boot 서버
  └─ 수신자 (카카오톡 링크)    ──▶  CloudFront  ──▶  S3

Spring Boot 단일 서버
  ├─ common
  │    └─ GoogleSheetsConfig       Sheets 서비스 빈. 키 JSON은 Parameter Store
  ├─ ObituaryController            폼 / 미리보기 / 생성 / 완료
  ├─ AdminController               수정 (X-Admin-Token 확인)
  └─ ObituaryService               ID 발급 · 저장 · 발행 · 수정 · 알림
       ├─ GoogleSheetsClient  ──▶  Google Sheets (obituaries)
       ├─ S3Publisher         ──▶  Thymeleaf 렌더  ──▶  S3 ({id}/index.html)
       └─ 운영자 알림          ──▶  운영자

운영자
  ├─ Google Sheets 브라우저에서 확인 (읽기 전용으로 씀)
  └─ PATCH /admin/obituaries/{id}  ──▶  AdminController
```

계층은 Controller → Service → 외부 연동(Sheets / S3)까지만 둔다. Repository/DAO 추상화는 만들지 않는다.

`common`의 설정 클래스는 외부 클라이언트 빈을 조립하는 자리로만 쓴다.
설정 값이 Parameter Store에서 프로퍼티로 들어오므로 어딘가에서는 빈을 조립해야 하고, 이걸 Client 안에 두면 테스트에서 갈아끼울 수 없다.
DB가 없으니 `DatabaseConfig`는 만들 것이 없고, `ExcelService`는 `GoogleSheetsClient`와 책임이 같아 이름만 둘로 늘어난다.

`AwsConfig`도 두지 않는다. `spring-cloud-aws-starter-s3`가 리전과 IAM 역할을 읽어 `S3Client` 빈을 이미 만들어 주므로, 같은 것을 다시 조립하는 빈 클래스만 남는다.
Sheets는 스타터가 없어서 `GoogleSheetsConfig`가 필요하다.

### 설정 값

AWS 자격 증명은 두지 않는다. 서버는 Terraform이 붙여 준 IAM 역할로 S3와 Parameter Store에 접근한다.
나머지 설정은 Parameter Store 한 곳에서 읽는다. 서버에 파일로 떨어뜨리거나 환경 변수로 늘어놓지 않는다.

| 파라미터 | 타입 | 용도 |
|---|---|---|
| `/obituary/s3-bucket` | String | 부고장 HTML을 올릴 버킷 |
| `/obituary/public-base-url` | String | 공유 링크에 붙일 도메인 (`https://obituary.example.com`) |
| `/obituary/google-credentials` | SecureString | 서비스 계정 키 JSON 본문 |
| `/obituary/spreadsheet-id` | SecureString | `부고장 관리` 스프레드시트 ID |
| `/obituary/admin-token` | SecureString | `X-Admin-Token` 헤더와 비교할 값 |

`spring-cloud-aws-starter-parameter-store`를 쓰고 `spring.config.import=aws-parameterstore:/obituary/` 한 줄을 둔다.
SecureString은 조회 시점에 복호화돼 일반 프로퍼티처럼 들어오므로 값을 가져오는 코드를 따로 쓰지 않는다.

서버에 남는 환경 변수는 리전(`AWS_REGION`) 하나뿐이다. 이것도 EC2에서는 인스턴스 메타데이터로 채워진다.

값을 읽는 곳은 프로필로 갈린다. 기본 프로필은 `local`이라 개발자는 아무것도 지정하지 않고 띄우면 되고, 서버는 `SPRING_PROFILES_ACTIVE`로 다른 프로필을 준다.

| 프로필 | 읽는 곳 | 없을 때 |
|---|---|---|
| `local` (기본) | 프로젝트 루트 `.env` (properties 형식, 커밋 안 함) | `application.yml`의 로컬 기본값으로 뜬다 |
| 그 외 (실서버) | Parameter Store `/obituary/` | 기동 실패 (`optional:` 없이 임포트한다) |

프로퍼티 이름은 두 곳이 같다(`google-credentials`, `spreadsheet-id`, …). 다만 서비스 계정 키 JSON은 여러 줄이라 properties 파일에 그대로 넣을 수 없어서, 로컬에서는 `google-credentials`에 **키 파일 경로**를 적는다. `GoogleSheetsConfig`가 값이 `{`로 시작하면 JSON 본문, 아니면 파일 경로로 읽는다.

---

## 3. 생성 Flow

```text
1. 작성자  GET  /obituaries/new             ──▶  입력 폼

2. 작성자  POST /obituaries/preview
     검증 실패  ──▶  폼 + 쉬운 에러 메시지
     검증 통과  ──▶  미리보기 화면

3. 작성자  POST /obituaries
     3-1. 고유 ID 발급 (랜덤 UUID, a1b2c3d4-e5f6-4718-9abc-0123456789ab)
     3-2. S3      {id}/index.html 업로드  ──▶  공유 링크 확정
     3-3. Sheets  1행 append (M열에 공유 링크 포함)
     3-4. 운영자 알림 (링크 + 수정 curl 명령)
     3-5. 302 Location: /obituaries/{id}/complete

4. 작성자  GET  /obituaries/{id}/complete   ──▶  부고장 보내기 / 링크 복사 버튼
```

공유는 카카오 SDK 없이 브라우저의 `navigator.share`로 한다. 휴대폰에서는 카카오톡이 들어 있는 공유 화면이 뜨고, 지원하지 않는 브라우저에서는 링크 복사로 넘어간다.
SDK를 붙이면 앱 키와 도메인 등록을 관리해야 하는데, 얻는 것이 공유 화면 모양뿐이라 MVP에서는 두지 않는다.

S3 업로드(3-2)를 시트 기록(3-3)보다 먼저 한다. 반대 순서로 하다 S3에서 실패하면 시트에는 있는데 열리지 않는 링크가 남고, 운영자는 정상 건과 구분할 수 없다.

---

## 4. 조회 Flow

```text
수신자  GET  https://obituary.example.com/a1b2c3d4-e5f6-4718-9abc-0123456789ab/

  CloudFront 캐시 있음                    ──▶  200  부고장 페이지
  CloudFront 캐시 없음
      └─ S3  a1b2c3d4-e5f6-4718-9abc-0123456789ab/index.html 있음     ──▶  200  부고장 페이지 (60초 캐시)
         S3  객체 없음                    ──▶  404  404.html
                                               "부고장을 찾을 수 없습니다"
```

애플리케이션 서버를 거치지 않는다. 앱이 죽어 있어도 이미 발행된 부고장은 열린다.

---

## 5. 수정 Flow

```text
1. 상주  ──▶  운영자에게 연락 ("빈소가 5호실로 바뀌었어요")

2. 운영자  PATCH /admin/obituaries/{id}   (X-Admin-Token, 고칠 필드만)
     2-1. 토큰 확인            불일치  ──▶  401
     2-2. 시트 조회 (A열 = ID)  없음   ──▶  404
     2-3. 넘어온 필드 검증      오류   ──▶  400
     2-4. 시트 행 갱신 (수정일시 기록)
     2-5. HTML 재렌더  ──▶  S3 {id}/index.html 덮어쓰기
     2-6. 200 "수정했습니다"

3. 최대 60초 뒤 CloudFront 캐시 만료  ──▶  수정본 노출
```

시트 갱신(2-4)을 S3 업로드(2-5)보다 먼저 한다. 시트가 원본이므로, S3에서 실패하면 다시 호출해 맞출 수 있다.

고객용 수정 화면은 만들지 않는다. 어르신이 수정 링크를 따로 보관하게 만드는 것보다 전화 한 통이 쉽고, 남의 부고장을 고치는 문제도 생기지 않는다.

---

## 6. 인프라

```text
카카오톡 공유 링크
        │
        ▼
obituary.example.com  (도메인)
        │
        ▼
CloudFront  HTTPS · TTL 60초
        │
        ▼
S3 버킷  {id}/index.html · 404.html
        ▲
        │ 발행 / 수정 업로드
        │
작성자 ──▶ Spring Boot JAR (서버 1대)
        │
        ▼
Google Sheets API  ──▶  Google Sheets (obituaries)
```

| 구성 | MVP 선택 |
|---|---|
| 실행 | 서버 1대에서 Spring Boot 실행 파일 구동 |
| 빌드 | Gradle (Java 17, Spring Boot 3.4.5) |
| 부고장 서빙 | S3 정적 파일. 키는 `{id}/index.html`, 에러 문서는 `404.html` |
| HTTPS · 도메인 | CloudFront. S3 웹사이트 엔드포인트는 HTTP만 지원해서 커스텀 도메인 HTTPS에는 CloudFront가 필요하다. 카카오톡 링크 미리보기와 신뢰감 확보에 필요 |
| 캐시 | CloudFront TTL 60초. 수정 후 무효화 호출 없이 1분 안에 반영된다 |
| 보관 기간 | S3 라이프사이클 규칙으로 60일 뒤 객체 자동 삭제. 삭제용 코드나 배치 작업은 만들지 않는다 |
| 데이터 저장 | Google Sheets API (서비스 계정) |
| 인증 정보 | AWS는 인스턴스 IAM 역할. 서비스 계정 키·스프레드시트 ID·운영자 토큰은 Parameter Store SecureString. 저장소에 커밋하지 않는다 |
| 인프라 관리 | Terraform. 버킷·CloudFront·IAM 역할·파라미터를 코드로 만든다 |
| 운영자 알림 | 서버에서 직접 발송 (채널은 구현 시점 결정) |
| 배포 | JAR 교체 후 재시작 |

### 접근 권한

- Google Sheets 문서는 서비스 계정 이메일에 편집 권한을 부여한다.
- 운영자는 같은 스프레드시트를 브라우저에서 열어 확인만 한다. 값을 고칠 때는 `PATCH /admin/obituaries/{id}`를 쓴다.
- S3 버킷은 CloudFront(OAC)에만 읽기를 허용하고, 쓰기는 서버의 IAM 역할만 가능하다.
- 서버 IAM 역할에 주는 권한은 세 가지뿐이다. 버킷 하위 객체 쓰기(`s3:PutObject`), `/obituary/` 파라미터 읽기(`ssm:GetParameter*`), SecureString 복호화(`kms:Decrypt`).
- `/admin/**`은 `/obituary/admin-token` 값과 `X-Admin-Token` 헤더 비교로만 막는다. 운영자가 한 명이라 Spring Security는 도입하지 않는다.

---

## 7. 발행된 HTML의 제약

- 페이지는 발행 시점의 값이 박힌 정적 HTML이다. 수정 API는 HTML을 다시 만들어 올리므로 최신값이 되지만(최대 60초 캐시 지연), API를 거치지 않고 시트만 브라우저에서 고치면 HTML은 그대로라 옛 값이 남는다.
- 템플릿(`obituary/view.html`)을 고치면 이미 발행된 부고장에는 반영되지 않는다. 전체에 반영하려면 모든 ID를 다시 발행해야 한다.
- ID는 추측하기 어려운 랜덤 문자열이어야 한다. S3에 올라간 순간 인증 없이 누구나 열 수 있는 URL이고, 상주 연락처가 들어 있다.
- 60일이 지나면 링크는 404가 된다. 장례가 끝난 뒤에도 상주 연락처가 계속 공개돼 있을 이유가 없어서 기한을 둔다.
- 수정 API가 객체를 덮어쓰면 라이프사이클 기한이 그 시점부터 다시 60일이다. 만료 직전에 고친 부고장은 예상보다 오래 남는다.
- 시트 행은 지우지 않는다. 페이지가 사라져도 어떤 부고장을 만들었는지는 운영 기록으로 남는다.

---

## 8. MVP에서 하지 않는 것

- 로드밸런서 / 다중 서버 / 오토스케일링
- 별도 DB, 캐시 서버
- 고객용 부고장 수정 / 삭제 화면
- 운영자용 수정 화면 (API 호출로 처리)
- 전체 부고장 일괄 재발행

트래픽이나 데이터가 실제로 문제가 될 때 하나씩 추가한다.

---

## 9. 변경 이력

### 2026-08-15 — MVP 구현하며 맞춘 내용

| 항목 | 전 | 후 | 이유 |
|---|---|---|---|
| `common/AwsConfig` | 둔다 | 두지 않는다 | `spring-cloud-aws-starter-s3`가 `S3Client` 빈을 이미 만든다. 같은 것을 다시 조립하는 빈 클래스만 남는다 |
| 부고장 ID | 8자 | 랜덤 UUID 전체 | 발행된 URL은 인증 없이 열리고 상주 연락처가 들어 있다. A열이 이미 ID 컬럼이라 새 컬럼은 만들지 않았다 |
| 공유 방식 | 카카오톡 공유 버튼 | `navigator.share` (미지원 시 링크 복사) | 앱 키·도메인 등록을 관리하지 않아도 된다 |
| 시트 저장 값 | 규정 없음 | 공백까지 적은 그대로 저장 · 길이 제한(공백 포함) | 시트에 남는 값과 부고장 페이지에 박히는 값이 같아야 한다 |
| 상주 | 규정 없음 | 이름·번호 각 한 명 | 이름은 여럿인데 번호는 하나면 조문객이 누구에게 연락할지 모른다 |
| 리전 설정 | `AWS_REGION` 환경 변수만 | `spring.cloud.aws.region.static`에 기본값 | 환경 변수가 없는 로컬에서도 뜬다. EC2에서는 환경 변수가 이긴다 |
