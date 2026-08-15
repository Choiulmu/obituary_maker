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
| A | 부고장ID | id | O | `a1b2c3d4` |
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
| M | 부고장링크 | shareUrl | O | `https://obituary.example.com/a1b2c3d4/` |

### 저장 규칙

- 날짜는 `yyyy-MM-dd`, 시각을 포함하면 `yyyy-MM-dd HH:mm` (KST) 문자열로 저장한다. 초는 쓰지 않는다.
- 예외로 생성일시·수정일시 같은 기록용 값만 `yyyy-MM-dd HH:mm:ss`로 초까지 남긴다. 같은 분에 들어온 건의 순서를 구분해야 하기 때문이다.
- 선택 항목 미입력 시 빈 문자열로 저장한다. (열 위치가 밀리지 않도록 항상 A~M 전체를 쓴다)
- 부고장링크(M)는 S3 업로드가 성공한 뒤에 기록한다. 업로드에 실패하면 시트에 행 자체를 남기지 않는다. 열리지 않는 링크가 시트에 있으면 운영자가 정상 건과 구분할 수 없다.
- 시트 값은 모두 문자열(`USER_ENTERED` 대신 `RAW`)로 기록한다. Sheets가 `010-1234-5678`을 날짜/수식으로 자동 변환하는 것을 막기 위함이다.
- 수정일시(C)는 생성 시 빈 문자열로 두고, 수정 API가 호출될 때만 채운다. 비어 있으면 한 번도 고치지 않은 건이라는 뜻이다.
- 시트를 바꾸는 것은 생성 시 append와 수정 API의 행 갱신 두 가지뿐이다. 운영자가 브라우저에서 시트를 직접 고치면 S3의 HTML과 값이 어긋난다.

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

---

## 3. 생성 Flow

```text
1. 작성자  GET  /obituaries/new             ──▶  입력 폼

2. 작성자  POST /obituaries/preview
     검증 실패  ──▶  폼 + 쉬운 에러 메시지
     검증 통과  ──▶  미리보기 화면

3. 작성자  POST /obituaries
     3-1. 고유 ID 발급 (a1b2c3d4)
     3-2. S3      {id}/index.html 업로드  ──▶  공유 링크 확정
     3-3. Sheets  1행 append (M열에 공유 링크 포함)
     3-4. 운영자 알림 (링크 + 수정 curl 명령)
     3-5. 302 Location: /obituaries/{id}/complete

4. 작성자  GET  /obituaries/{id}/complete   ──▶  링크 복사 / 카카오톡 공유 버튼
```

S3 업로드(3-2)를 시트 기록(3-3)보다 먼저 한다. 반대 순서로 하다 S3에서 실패하면 시트에는 있는데 열리지 않는 링크가 남고, 운영자는 정상 건과 구분할 수 없다.

---

## 4. 조회 Flow

```text
수신자  GET  https://obituary.example.com/a1b2c3d4/

  CloudFront 캐시 있음                    ──▶  200  부고장 페이지
  CloudFront 캐시 없음
      └─ S3  a1b2c3d4/index.html 있음     ──▶  200  부고장 페이지 (60초 캐시)
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
| 인증 정보 | 서비스 계정 키, 스프레드시트 ID, AWS 자격 증명, 운영자 토큰은 환경 변수로 주입. 저장소에 커밋하지 않는다 |
| 운영자 알림 | 서버에서 직접 발송 (채널은 구현 시점 결정) |
| 배포 | JAR 교체 후 재시작 |

### 접근 권한

- Google Sheets 문서는 서비스 계정 이메일에 편집 권한을 부여한다.
- 운영자는 같은 스프레드시트를 브라우저에서 열어 확인만 한다. 값을 고칠 때는 `PATCH /admin/obituaries/{id}`를 쓴다.
- S3 버킷은 CloudFront(OAC)에만 읽기를 허용하고, 쓰기는 애플리케이션 IAM 사용자만 가능하다.
- `/admin/**`은 환경 변수 `ADMIN_TOKEN`과 `X-Admin-Token` 헤더 비교로만 막는다. 운영자가 한 명이라 Spring Security는 도입하지 않는다.

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
