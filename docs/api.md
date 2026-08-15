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

| 필드 | 이름 | 필수 | 예시 |
|---|---|---|---|
| `name` | 고인 이름 | O | 홍길동 |
| `deathDate` | 별세일 | O | 2026-08-14 |
| `funeralHome` | 장례식장 | O | 서울추모공원 |
| `room` | 빈소 | O | 3호실 |
| `departureDate` | 발인일 | O | 2026-08-16 |
| `mournerName` | 상주 이름 | O | 홍철수 |
| `mournerPhone` | 상주 연락처 | O | 010-1234-5678 |
| `address` | 장례식장 주소 | X | 서울시 서초구 원지동 산4-1 |
| `directions` | 오시는 길 | X | 3호선 양재역 4번 출구 셔틀버스 |
| `account` | 조의금 계좌 | X | 국민 123456-01-123456 홍철수 |
| `message` | 추가 안내 문구 | X | 조화는 정중히 사양합니다 |

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
성함을 입력해 주세요.
연락처를 010-1234-5678 형식으로 입력해 주세요.
```

### POST `/obituaries`
- Input: 미리보기와 동일한 폼 전체
- 처리: 고유 ID 발급 → Google Sheets 기록 → 부고장 HTML 렌더 → S3 `{id}/index.html` 업로드 → 운영자 알림
- Response (성공): `302 Location: /obituaries/a1b2c3d4/complete`
- Response (실패): `200` → `obituary/form.html` + `부고장을 만들지 못했습니다. 잠시 후 다시 시도해 주세요.`

시트 기록과 S3 업로드 중 하나만 성공하면 부고장이 반쪽으로 남는다.
S3 업로드를 먼저 하고 시트 기록을 나중에 한다. 시트에 없는 부고장은 운영자가 알 수 없으므로 순서를 바꾸지 않는다.

### GET `/obituaries/{id}/complete`
- Input: path `id` = `a1b2c3d4`
- Output: `obituary/complete.html`
- Response: `200`
```
model.shareUrl = https://obituary.example.com/a1b2c3d4/
model.name     = 홍길동
```
- 화면: 링크 복사 / 카카오톡 공유 / 부고장 보기 버튼

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
message        조화는 정중히 사양합니다
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

- Input: path `id` = `a1b2c3d4`, header `X-Admin-Token`, form body (고칠 필드만)
- 처리: 시트 행 조회 → 넘어온 필드만 덮어쓰기 → 시트 행 갱신(수정일시 기록) → HTML 재렌더 → S3 `{id}/index.html` 덮어쓰기
- Response (성공): `200` `수정했습니다.`
- Response (값 형식 오류): `400` + 어떤 값이 잘못됐는지
- Response (토큰 불일치): `401`
- Response (시트에 없는 ID): `404`

```
curl -X PATCH https://obituary.example.com/admin/obituaries/a1b2c3d4 \
  -H "X-Admin-Token: $ADMIN_TOKEN" \
  -d "room=5호실" \
  -d "departureDate=2026-08-17"
```

PUT이 아니라 PATCH인 이유는 운영자가 보통 한두 항목만 고치기 때문이다.
PUT이면 매번 필수 7개를 전부 다시 보내야 하고, 하나를 빠뜨리면 멀쩡한 값이 지워진다.

넘어온 필드만 검증한다. 검증 규칙은 생성 폼과 같다. 빈 문자열을 보내면 선택 항목은 지워지고, 필수 항목은 `400`이다.

CloudFront 캐시 TTL이 60초라 무효화 없이 1분 안에 반영된다.

---

## 참고

- 운영자 알림은 부고장 생성 시 서버 내부에서 처리하며 별도 API로 노출하지 않는다. 알림에 수정 curl 명령을 함께 넣는다.
- 고객용 수정 / 삭제 API는 만들지 않는다. 수정 요청은 상주가 운영자에게 연락해 처리한다.
- 시트를 브라우저에서 직접 고치면 부고장 페이지에는 반영되지 않는다. 수정은 이 API로 한다.
- 삭제는 S3 객체를 직접 지운다. 운영자가 직접 하는 일이라 API로 만들지 않는다.
