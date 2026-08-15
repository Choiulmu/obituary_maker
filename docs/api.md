# API 목록

Thymeleaf 기반 SSR이므로 JSON API가 아닌 **HTML 페이지 / 폼 전송 엔드포인트**로 정의한다.
(`Content-Type: application/x-www-form-urlencoded`)

발행된 부고장 페이지는 애플리케이션이 아니라 **S3에 올라간 정적 HTML**이 응답한다.
앱은 부고장을 만들고(발행) 고치는(수정 + 재발행) 것까지만 담당한다.

| 도메인 | Method | Path | 설명 |
|---|---|---|---|
| 공통 | GET | `/` | 랜딩 (부고장 만들기 버튼) |
| 부고장 작성 | GET | `/obituaries/new` | 정보 입력 폼 |
| 부고장 작성 | POST | `/obituaries/preview` | 미리보기 |
| 부고장 작성 | POST | `/obituaries` | 부고장 생성 + 고유 링크 발급 |
| 부고장 작성 | GET | `/obituaries/{id}/complete` | 생성 완료 / 링크 공유 |
| 부고장 조회 | GET | `https://obituary.example.com/{id}/` | 공유용 부고장 페이지 (S3 정적 HTML, 앱 아님) |
| 운영자 | PATCH | `/admin/obituaries/{id}` | 부고장 수정 (시트 갱신 + 페이지 재발행) |

---

## 1. 공통

### GET `/`
- Input: 없음
- Output: `index.html`
- Response: `200` (모델 없음)

---

## 2. 부고장 작성

### 입력 필드

| 필드 | 이름 | 필수 | 최대 길이 | 예시 |
|---|---|---|---|---|
| `name` | 고인 이름 | O | 20 | 홍길동 |
| `deathDate` | 별세일 | O | - | 2026-08-14 |
| `funeralHome` | 장례식장 | O | 30 | 서울추모공원 |
| `room` | 빈소 | O | 20 | 3호실 |
| `departureDate` | 발인일 | O | - | 2026-08-16 |
| `mournerName` | 상주 이름 | O | 30 | 홍철수 |
| `mournerPhone` | 상주 연락처 | O | 13 | 010-1234-5678 |
| `address` | 장례식장 주소 | X | 100 | 서울시 서초구 원지동 산4-1 |
| `account` | 조의금 계좌 | X | 50 | 국민 123456-01-123456 홍철수 |

최대 길이는 글자 수 기준이다. 한글 한 글자를 1로 센다.

한국어 입력을 기준으로 잡은 값이다. 상주 이름은 형제자매를 함께 적는 경우가 있어(`홍철수, 홍영희`) 이름 한 개 길이보다 넉넉하게 두고,
빈소는 `지하1층 특2호실`처럼 층과 호실을 같이 적는 경우까지 들어가게 둔다.
길이를 두는 이유는 저장 공간이 아니라 부고장 화면이다. 이 길이를 넘으면 모바일 화면에서 줄이 밀려 읽기 어려워진다.

날짜(`deathDate`, `departureDate`)는 `yyyy-MM-dd` 형식으로만 받으므로 길이 제한을 따로 두지 않는다.
연락처는 형식 검사(`0##-###?#-####`)가 길이까지 정한다.

입력 폼에는 같은 값을 `maxlength`로 두어 애초에 더 칠 수 없게 한다. 서버 검증은 폼을 거치지 않는 요청(운영자 수정 API)까지 막기 위해 그대로 둔다.

### GET `/obituaries/new`
- Output: `obituary/form.html`
- Response: `200`, model `obituary` (빈 폼 객체)

### POST `/obituaries/preview`
- Input (form)
```
name=홍길동
deathDate=2026-08-14
funeralHome=서울추모공원
room=3호실
departureDate=2026-08-16
mournerName=홍철수
mournerPhone=010-1234-5678
account=국민 123456-01-123456 홍철수
```
- Response (성공): `200` → `obituary/preview.html`, model `obituary` = 입력값 그대로
- Response (검증 실패): `200` → `obituary/form.html` + 에러 메시지
```
고인의 성함을 입력해 주세요.
연락처를 010-1234-5678 형식으로 입력해 주세요.
고인의 성함은 20자까지 적을 수 있습니다.
별세일을 다시 확인해 주세요.
```

### POST `/obituaries`
- Input: 미리보기와 동일한 폼 전체
- 처리: 고유 ID 발급 → 부고장 HTML 렌더 → S3 `{id}/index.html` 업로드 → 공유 링크 포함해 Google Sheets 기록 → 운영자 알림
- Response (성공): `302 Location: /obituaries/a1b2c3d4e5f60718/complete`
- Response (실패): `200` → `obituary/form.html` + `부고장을 만들지 못했습니다. 잠시 후 다시 시도해 주세요.`

시트 기록과 S3 업로드 중 하나만 성공하면 부고장이 반쪽으로 남는다.
S3 업로드를 먼저 하고 시트 기록을 나중에 한다. 시트에 없는 부고장은 운영자가 알 수 없으므로 순서를 바꾸지 않는다.

### GET `/obituaries/{id}/complete`
- Input: path `id` = `a1b2c3d4e5f60718`
- Output: `obituary/complete.html`
- Response: `200`
```
model.shareUrl = https://obituary.example.com/a1b2c3d4e5f60718/
model.name     = 홍길동
```
- 화면: 부고장 보내기 / 링크 복사하기 / 부고장 보기 버튼

`model.name`은 생성 요청에서 넘어온 flash 속성이라 새로고침하면 사라진다. 링크는 ID로 다시 만들 수 있으므로 새로고침해도 남는다.

공유는 카카오 SDK 대신 브라우저의 `navigator.share`를 쓴다. 휴대폰에서 누르면 카카오톡이 들어 있는 공유 화면이 그대로 뜬다.
SDK를 쓰려면 앱 키와 도메인 등록이 필요하고 값을 하나 더 관리해야 하는데, 얻는 것이 공유 화면 모양뿐이라 MVP에서는 두지 않는다.
`navigator.share`가 없는 브라우저(주로 PC)에서는 링크 복사로 넘어간다.

---

## 3. 부고장 조회

### GET `https://obituary.example.com/{id}/`

애플리케이션 엔드포인트가 아니다. S3에 올라간 정적 HTML을 CloudFront가 그대로 내려준다.

- 내용: 발행 시점의 `obituary/view.html` 렌더 결과 (모바일 기준)
- Response (성공): `200`
```
name           홍길동
deathDate      2026년 8월 14일
room           3호실
funeralHome    서울추모공원
address        서울시 서초구 원지동 산4-1
departureDate  2026년 8월 16일
mournerName    홍철수
mournerPhone   010-1234-5678
account        국민 123456-01-123456 홍철수
```
- Response (없는 링크): `404` → S3 에러 문서 `404.html`
```
부고장을 찾을 수 없습니다. 링크를 다시 확인해 주세요.
```

---

## 4. 운영자

### PATCH `/admin/obituaries/{id}`

상주가 잘못된 내용을 알려오면 운영자가 호출한다.
시트 값을 고치고 부고장 페이지를 다시 발행하는 것까지 한 번에 처리한다.

- Input: path `id` = `a1b2c3d4e5f60718`, header `X-Admin-Token`, form body (고칠 필드만)
- 처리: 시트 행 조회 → 넘어온 필드만 덮어쓰기 → 시트 행 갱신(수정일시 기록) → HTML 재렌더 → S3 `{id}/index.html` 덮어쓰기
- Response (성공): `200` `수정했습니다.`
- Response (값 형식 오류): `400` + 어떤 값이 잘못됐는지
- Response (토큰 불일치): `401`
- Response (시트에 없는 ID): `404`

```
curl -X PATCH https://obituary.example.com/admin/obituaries/a1b2c3d4e5f60718 \
  -H "X-Admin-Token: $ADMIN_TOKEN" \
  -d "room=5호실" \
  -d "departureDate=2026-08-17"
```

PUT이 아니라 PATCH인 이유는 운영자가 보통 한두 항목만 고치기 때문이다.
PUT이면 매번 필수 7개를 전부 다시 보내야 하고, 하나를 빠뜨리면 멀쩡한 값이 지워진다.

넘어온 필드를 시트에서 읽어온 값 위에 덮어쓴 뒤, 합쳐진 부고장 전체를 생성 폼과 같은 규칙으로 검증한다.
빈 문자열을 보내면 선택 항목은 지워지고, 필수 항목은 `400`이다. 길이 제한도 그대로 걸린다.

고칠 수 있는 항목은 [입력 필드](#입력-필드) 9개뿐이다. `id`·`createdAt`·`shareUrl`처럼 목록에 없는 이름을 보내면 아무것도 고치지 않고 `400`이다.
오타 난 필드 이름을 조용히 넘기면 운영자는 고쳤다고 생각하는데 값은 그대로인 상태가 된다.

CloudFront 캐시 TTL이 60초라 무효화 없이 1분 안에 반영된다.

---

## 참고

- 운영자 알림은 부고장 생성 시 서버 내부에서 처리하며 별도 API로 노출하지 않는다. 알림에 수정 curl 명령을 함께 넣는다.
- 고객용 수정 / 삭제 API는 만들지 않는다. 수정 요청은 상주가 운영자에게 연락해 처리한다.
- 시트를 브라우저에서 직접 고치면 부고장 페이지에는 반영되지 않는다. 수정은 이 API로 한다.
- 삭제 API는 만들지 않는다. 발행 후 60일이 지나면 S3 라이프사이클 규칙이 객체를 자동으로 지운다. 운영자가 직접 지우는 일은 없다.

---

## 변경 이력

### 2026-08-15 — MVP 구현하며 맞춘 내용

| 항목 | 전 | 후 | 이유 |
|---|---|---|---|
| 입력 길이 제한 | 없음 | 필드별 20~100자 | 긴 값이 들어오면 모바일 부고장 화면에서 줄이 밀린다 |
| 부고장 ID | 8자 (`a1b2c3d4`) | 16자 (`a1b2c3d4e5f60718`) | 발행된 URL은 인증 없이 열리고 상주 연락처가 들어 있다. 손으로 칠 일이 없어 길어도 불편하지 않다 |
| 공유 방식 | 카카오톡 공유 버튼 | `navigator.share` (미지원 시 링크 복사) | 앱 키·도메인 등록을 관리하지 않고도 카카오톡으로 보낼 수 있다 |
| 수정 API 검증 | 넘어온 필드만 검증 | 시트 값에 덮어쓴 뒤 전체 검증 | 결과는 같고, 검증 규칙을 생성 폼과 한 벌로 유지한다 |
| 수정 API 필드 | 규정 없음 | 목록에 없는 이름은 `400` | 오타를 조용히 넘기면 고쳤다고 생각하는데 값이 그대로다 |
