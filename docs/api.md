# API 목록

Thymeleaf 기반 SSR이므로 JSON API가 아닌 **HTML 페이지 / 폼 전송 엔드포인트**로 정의한다.
(`Content-Type: application/x-www-form-urlencoded`, 사진 포함 시 `multipart/form-data`)

| 도메인 | Method | Path | 설명 |
|---|---|---|---|
| 공통 | GET | `/` | 랜딩 (부고장 만들기 버튼) |
| 부고장 작성 | GET | `/obituaries/new` | 정보 입력 폼 |
| 부고장 작성 | POST | `/obituaries/preview` | 미리보기 |
| 부고장 작성 | POST | `/obituaries` | 부고장 생성 + 고유 링크 발급 |
| 부고장 작성 | GET | `/obituaries/{id}/complete` | 생성 완료 / 링크 공유 |
| 부고장 조회 | GET | `/obituaries/{id}` | 공유용 부고장 페이지 |

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
| deceasedName | 고인 성함 | O | 홍길동 |
| deathDate | 별세일 | O | 2026-08-14 |
| funeralHall | 장례식장 | O | 서울추모공원 |
| mortuaryRoom | 빈소(호실) | O | 3호실 |
| coffinOutDate | 발인일 | O | 2026-08-16 |
| chiefMournerName | 상주 이름 | O | 홍철수 |
| chiefMournerPhone | 상주 연락처 | O | 010-1234-5678 |
| photo | 고인 사진 | X | (파일) |
| funeralHallAddress | 장례식장 주소 | X | 서울시 서초구 원지동 산4-1 |
| directions | 오시는 길 | X | 3호선 양재역 4번 출구 셔틀버스 |
| condolenceAccount | 조의금 계좌 | X | 국민 123456-01-123456 홍철수 |
| additionalMessage | 추가 안내 문구 | X | 조화는 정중히 사양합니다 |

### GET `/obituaries/new`
- Output: `obituary/form.html`
- Response: `200`, model `obituary` (빈 폼 객체)

### POST `/obituaries/preview`
- Input (form)
```
deceasedName=홍길동
deathDate=2026-08-14
funeralHall=서울추모공원
mortuaryRoom=3호실
coffinOutDate=2026-08-16
chiefMournerName=홍철수
chiefMournerPhone=010-1234-5678
condolenceAccount=국민 123456-01-123456 홍철수
```
- Response (성공): `200` → `obituary/preview.html`, model `obituary` = 입력값 그대로
- Response (검증 실패): `200` → `obituary/form.html` + 에러 메시지
```
성함을 입력해 주세요.
연락처를 010-1234-5678 형식으로 입력해 주세요.
```

### POST `/obituaries`
- Input: 미리보기와 동일한 폼 전체 (사진 포함 시 multipart)
- 처리: 고유 ID 발급 → Google Sheets 기록 → 운영자 알림
- Response (성공): `302 Location: /obituaries/a1b2c3d4/complete`
- Response (실패): `200` → `obituary/form.html` + `부고장을 만들지 못했습니다. 잠시 후 다시 시도해 주세요.`

### GET `/obituaries/{id}/complete`
- Input: path `id` = `a1b2c3d4`
- Output: `obituary/complete.html`
- Response: `200`
```
model.shareUrl = https://obituary.example.com/obituaries/a1b2c3d4
model.deceasedName = 홍길동
```
- 화면: 링크 복사 / 카카오톡 공유 / 부고장 보기 버튼

---

## 3. 부고장 조회

### GET `/obituaries/{id}`
- Input: path `id` = `a1b2c3d4`
- Output: `obituary/view.html` (모바일 기준)
- Response (성공): `200`
```
photoUrl            /images/a1b2c3d4.jpg
deceasedName        홍길동
deathDate           2026년 8월 14일
mortuaryRoom        3호실
funeralHall         서울추모공원
funeralHallAddress  서울시 서초구 원지동 산4-1
coffinOutDate       2026년 8월 16일
chiefMournerName    홍철수
chiefMournerPhone   010-1234-5678
condolenceAccount   국민 123456-01-123456 홍철수
additionalMessage   조화는 정중히 사양합니다
```
- Response (없는 링크): `404` → `error/404.html`
```
부고장을 찾을 수 없습니다. 링크를 다시 확인해 주세요.
```

---

## 참고

- 운영자 알림은 부고장 생성 시 서버 내부에서 처리하며 별도 API로 노출하지 않는다.
- 수정 / 삭제 API는 MVP 범위 밖이다. (요청 발생 시 추가)
- 사진 저장 위치(로컬 / 외부 스토리지)는 구현 시점에 결정한다.
