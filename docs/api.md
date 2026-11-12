<aside>
🗂️

표로 관리하기: 아울리 API 명세서

50개 API의 화면·메서드·endpoint·우선순위·검토 여부·담당자를 관리할 수 있습니다. 각 행에 요청·응답 상세를 넣었습니다. 담당자와 우선순위는 팀에서 지정하며, API 내용 변경 시 이 문서·OpenAPI 파일·표의 해당 행을 함께 갱신합니다.

</aside>

<aside>
📘

재검토한 아울리 ERD를 기준으로 정리한 API 설계 초안입니다.

50개 API의 요청·응답, 권한, 상태 전이, 오류와 ERD 연결을 정의했습니다. 기획에서 확인된 동작과 구현을 위한 제안을 구분했으며, 실제 서버 구현·배포나 미확정 정책의 승인을 의미하지 않습니다.

</aside>

작성일: 2026년 11월 5일 · 버전: 0.1.0-draft · 기준 시간대: Asia/Seoul

## 기준 문서와 파일

- 아울리 ERD · 화면 정의서 기준 (초안) — 25개 테이블·54개 관계, 재검토 수정 반영.
- 아울리 ERD 코드 모음 · Mermaid — 상세 필드 전체를 포함한 통합 코드.
- Figma 원본 화면 정의서 — 기획 담당이 설명한 버전. 선송금 → 실제 요금 정산 → 차액 환급 흐름 적용.
- 솔루션 핵심 기능, 범위 도출 · 공공데이터 활용 전략 — 매칭·수요 집계 근거.
- 첨부 파일은 이 문서와 같은 정의에서 만든 OpenAPI 3.1.0 JSON입니다. 서버 주소는 실제 운영 도메인 대신 상대 경로 `/api/v1`로 두었습니다.

아울리 API 0.1 · OpenAPI JSON

아울리 API 0.1 · OpenAPI JSON

**예시 데이터 안내:** 요청·응답 예시의 ID·사용자·은행·계좌·학교 이메일 도메인·요금·좌표·만료 시각은 가상입니다. 실제 인증 대상 대학과 도메인은 API-01의 기준정보를 사용합니다. 예시의 7일/세션 만료 시각을 미확정 기산점·TTL의 확정값으로 사용하지 않습니다.

## 1. 공통 계약

### 경로·값·응답

| 항목 | 규칙 |
| --- | --- |
| Base URL | `/api/v1` · HTTPS 전제 |
| JSON 필드 | camelCase. DB의 snake_case/암호화 컬럼을 그대로 직렬화하지 않음 |
| 식별자 | bigint ID는 JSON 문자열. 예: `"401"` |
| 금액 | 원 단위 정수. 클라이언트가 예상 분담액·환급액·송금액을 결정하지 않음 |
| 시각 | 날짜와 시간대가 있는 RFC 3339. 예: `2026-11-05T23:30:00+09:00`. 저장·비교는 UTC, 화면과 요일은 Asia/Seoul |
| 30분 슬롯 | 실제 출발 날짜와 `:00` 또는 `:30` 시각. 자정 뒤 출발은 다음 날 날짜 사용 |
| 요일 | 0=일요일, 1=월요일, …, 6=토요일 |
| 필수·NULL | DTO 표에 구분. NULL은 아직 결정되지 않았거나 제공 불가능한 값이며 0원/빈 문자열로 대체하지 않음 |
| 요청 검증 | 정의에 없는 필드는 422. 역할·소유자·상태·계산 금액은 서버가 결정 |
| 문자열 제한 | 가입 이름은 화면 정의서 v0.3 기준 앞뒤 공백 제거 후 2~20자. 메시지·신고 등 타 기능의 초안 제한은 해당 기능 구현 시 v0.3과 대조 |
| 성공 | 단건 `{"data": {...}}`, 목록 `{"data": [...]}`. 204는 body 없음 |
| 페이지 | 일반 목록은 cursor·limit(기본 20, 최대 100)과 meta.nextCursor/hasMore. 메시지는 afterId/beforeId 및 MessagePageMeta 사용 |
| 오류 | `{"error":{"code","message","details","traceId"}}`. 민감정보·타인 자원 존재 여부를 details에 넣지 않음 |

### 인증·권한

**채택한 인증 방식:** 동일 origin 웹 앱을 기준으로 Spring Security + Spring Session JDBC 서버 세션과 `owlly_session` 쿠키를 사용합니다. 세션은 PostgreSQL의 인프라 테이블에 저장합니다. Secure·HttpOnly·SameSite=Lax를 적용하며 세션 원문을 JSON이나 URL에 넣지 않습니다. localhost HTTP 개발에서만 Secure를 해제합니다. 화면 정의서 v0.4의 로그인 유지 30일에 맞춰 서버 유휴 만료 기본값은 30일(`SESSION_TIMEOUT=30d`)이며 요청 시 연장됩니다. 지속 쿠키의 Max-Age도 같은 설정을 따르며 발급 시점부터 30일입니다. 브라우저 종료 후에도 쿠키를 사용할 수 있고, 서버 세션이 만료·폐기되면 쿠키가 남아 있어도 인증되지 않습니다. 절대 세션 만료·재인증 기준은 별도 정책입니다.

- 로그인된 POST/PUT/PATCH/DELETE에는 `X-CSRF-Token`을 요구합니다. 토큰은 로그인 또는 GET /auth/session 응답에서 받습니다. 공개 인증 POST는 `ALLOWED_ORIGINS`에 등록된 `Origin` 헤더가 필수이며 누락/불허는 403 FORBIDDEN입니다. 이메일 인증 제한과 관계자 로그인 연속 5회 실패 시 30분 잠금을 구현했습니다. 학생 로그인 API 자체의 추가 속도 제한 기준은 미정입니다.
- 서버 세션은 Spring Session의 `SPRING_SESSION`/`SPRING_SESSION_ATTRIBUTES` 인프라 테이블로 관리하며 업무 ERD와 구분합니다. 인증 증표·멱등 요청 캐시는 각 기능의 인프라 저장소 설계 항목입니다.
- 학생 가입은 이메일 인증 후 계정·프로필·인증 증표 소비를 하나의 트랜잭션에서 저장하고, 커밋 성공 후 서버 세션을 발급합니다. 기존 학생은 학교 이메일 OTP를 새로 인증해 받은 증표로 재로그인합니다. 미가입이면 404 ACCOUNT_NOT_FOUND이며 증표 소비는 롤백되어 회원가입에 이어 쓸 수 있습니다. 관계자는 운영팀이 발급한 STAFF 계정의 이메일/비밀번호로 API-09에서 로그인합니다.
- 역할이 맞지 않으면 403, 타인의 개별 자원이거나 공개할 수 없는 ID이면 원칙적으로 404로 동일 처리합니다. 요청의 userId·senderId·createdBy·role을 신뢰하지 않습니다.
- 계정 상태와 역할을 매 요청 DB에서 확인합니다. 탈퇴/삭제 계정은 세션을 폐기하고 401을 반환합니다. 정지 계정은 현재 세션 조회·로그아웃만 허용하며 나머지는 403 ACCOUNT_SUSPENDED입니다. 이미 발생한 선송금·환급 확인을 위한 예외 접근 범위는 **기획 확인 사항**이며 정책 확정 후 확장합니다.

| 권한 | 대상 |
| --- | --- |
| `PUBLIC` | 비로그인 |
| `AUTHENTICATED` | 로그인 사용자 |
| `STUDENT` | 학생 |
| `STAFF` | 학교 관계자 |
| `OWNER_JOURNEY` | 해당 귀가의 학생 |
| `OWNER_REQUEST` | 해당 신청의 학생 |
| `PARTY_MEMBER` | 해당 팟 참여자 |
| `ROOM_MEMBER` | 해당 방 접근권한을 가진 참여자 |
| `REPRESENTATIVE` | 해당 팟 대표 |
| `ACCOUNT_OWNER` | 본인 계좌의 팟 참여자 |
| `TRANSFER_PAYER` | 해당 이체의 송금자 |
| `TRANSFER_PAYEE` | 해당 이체의 수취자 |
| `REPORT_OWNER` | 리포트를 만든 관계자 |

### 멱등성과 동시 수정

- 생성·상태 변경 중 개별 항목에 표시한 API는 `Idempotency-Key: UUID`를 요구합니다. 같은 사용자·경로·키·요청은 기존 결과를 반환하고, 같은 키의 다른 요청은 409 IDEMPOTENCY_CONFLICT입니다. 만료된 인증 결과를 재사용하지 않습니다.
- 귀가·신청·메시지의 clientJourneyKey/clientRequestKey/clientMessageId도 DB 고유키로 검증합니다. **한 번의 귀가 재등록에는 같은 journeyId를 유지하고 새 clientRequestKey를 사용합니다.**
- `If-Match` 표시가 있는 명령은 직전 해당 자원 GET/수정 성공 응답의 ETag를 전달합니다. 누락은 428, 값이 오래됐으면 412입니다. ETag는 관련 레코드 상태를 함께 반영하는 강한 검증자로 계산하는 제안이며 ERD의 별도 필드로 추가하지 않았습니다.
- 재시도는 서버에 이미 완료된 동일 멱등 요청을 먼저 확인한 뒤 ETag를 검사합니다. 새 요청은 잠금·조건부 갱신으로 보호합니다.
- **오류 시 기록 예외:** 이메일 인증은 잘못된 코드의 실패 횟수·멱등 요청 결과를 기록하고, 발급 정보를 커밋한 후 SMTP 발송이 실패한 경우에도 발급 건을 유지합니다. 관계자 로그인도 실패 횟수·잠금·멱등 기록을 먼저 커밋하고 401/403/429를 반환합니다. 그 밖의 검증 실패는 업무 변경을 커밋하지 않습니다.

### 응답 노출 범위

| 대상 | 반환 가능한 내용 | 반환하지 않는 내용 |
| --- | --- | --- |
| 본인 프로필 | 본인 실명·기본 하차 핀 | 타인 프로필 |
| 일반 팟 멤버 | 이름 일부·학교·동·동성 여부·본인 정산 | 타인 정확한 핀·실명·환급 계좌·내부 userId |
| 대표 | 정산 관리에 필요한 전원 금액·본인이 환급할 계좌 | 계좌의 방 범위를 벗어난 장기 보관 정보 |
| 수취 계좌 조회 | 해당 이체의 송금자에게만 계좌번호 원문 | 방/이체 권한 없는 사람의 조회 |
| 학교 관계자 | 승인된 학교 범위의 집계·근거·리포트 | 사용자 원자료·채팅·정확한 핀·계좌 |
| 알림·로그 | 최소한의 사건/식별자와 마스킹 요약 | 비밀번호·OTP·인증 증표·계좌·현재 GPS 원문 |

개인정보·계좌·영수증 URL 응답은 `Cache-Control: no-store`를 적용합니다. 현재 GPS는 비교 계산에만 쓰고 DB·요청/오류 로그·분석 이벤트·캐시 키에 남기지 않습니다.

## 2. 화면 흐름과 상태 변경

| 흐름 | API 순서·서버 작업 |
| --- | --- |
| 학생 가입 | 대학/프로필 선택지 → 이메일 발급 → 코드 검증 → 학생 가입 → 내 프로필 |
| 귀가 비교 | 거점 목록 → travel-options → journeys 생성 |
| 매칭 | matching-slots → ride-requests 생성 → 신청 결과 조회 → 팟 상세 → 본인 confirm |
| 매칭 실패 | 같은 journeyId에 새 신청 또는 choice로 BUS/SOLO 기록 |
| 선송금 | 대표 계좌 등록 → 정산 조회 → 멤버 수취 계좌 조회 → sent → 대표 received |
| 탑승 | 전원 선송금 수령 확인 → 대표 taxi-call-confirmation → 각자 boarding |
| 실제 정산 | 대표 영수증(선택 경로)·순서 수정 → preview → finalize |
| 환급 | 멤버 환급 계좌 등록 → 대표 수취 계좌 조회 → sent → 멤버 received |
| 노선 제안 | 수요 후보/근거 조회 → 조치 선택 → 리포트 생성 → exports → 상태 조회 → download |
- `ride_requests`: WAITING → MATCHED / FAILED / CANCELLED / EXPIRED. 매칭 후 이탈은 팟 명령으로 처리하고 해당 신청도 종료합니다. 과거 MATCHED 행이 남아 있더라도 활성 판정은 팟의 현재 진행 상태와 함께 판단합니다.
- `party_members`: 본인 확정·거절·이탈과 탑승 확인은 별도 필드입니다. 생성/취소 중 0~1명, 매칭 성립·확정 시 2~4명을 검증합니다.
- `transfers`: PENDING → SENT → RECEIVED. 수취인이 먼저 실제 입금을 확인한 경우 PENDING → RECEIVED 허용은 제안입니다. 어떤 확인 요청도 금액·송금자·수취자를 변경하지 않습니다.
- `settlements`: 예상 계산 → 선송금 → 실제 요금 대기 → 환급 → 완료. 검토가 필요한 경우 REVIEW_REQUIRED. 확정된 정산/경로는 수정하지 않습니다.
- `stakeholder_reports`: DRAFT → GENERATING → READY 또는 FAILED. FAILED 재시도는 같은 리포트로 가능하며 READY 결과를 새 계산으로 덮어쓰지 않습니다.

**서버 내부 작업:** 선착순 매칭·팟/하차 순서·예상 정산/개인 항목/PENDING 선송금 생성, 결과 알림, 확정 마감 처리(마감까지 확정하지 않은 멤버는 팟에서 빠지고 신청은 FAILED, 대표가 확정하지 않았거나 확정한 사람이 2명 미만이면 팟 해산, 아니면 확정한 사람끼리 경로를 다시 계산해 PREPAYING; 기획 답변 v2 #9), 출발 +5분 미입금 처리, 환급 알림·제재, 보존 만료 삭제, 수요 산출, 파일 생성은 스케줄러/작업 큐 영역입니다. 학생·관계자에게 임의 매칭 확정·추정치 편집·직접 수요 산출 권한을 주는 공개 API는 두지 않았습니다. 미정 정책이 필요한 작업은 설정 확정 전 활성화하지 않습니다.

## 3. API 목록

| ID | 메서드 | 경로 | 기능 | 권한 |
| --- | --- | --- | --- | --- |
| API-01 | GET | `/universities` | 참여 대학·인증 도메인 조회 | 비로그인 |
| API-02 | GET | `/profile-options` | 프로필 선택값 조회 | 비로그인 |
| API-03 | GET | `/banks` | 수취 계좌 은행 목록 | 학생 |
| API-04 | GET | `/pickup-points` | 고정 출발 거점 목록 | 학생 |
| API-05 | POST | `/auth/email-verifications` | 학교 이메일 인증번호 발급·재발급 | 비로그인 |
| API-06 | POST | `/auth/email-verifications/{verificationId}/verify` | 이메일 코드 검증 | 비로그인 |
| API-07 | POST | `/auth/student-registrations` | 학생 가입·프로필 생성 | 비로그인 |
| API-08 | POST | `/auth/student-sessions` | 학생 재로그인 | 비로그인 |
| API-09 | POST | `/auth/staff-sessions` | 관계자 로그인 | 비로그인 |
| API-10 | GET | `/auth/session` | 현재 계정·세션 확인 | 로그인 사용자 |
| API-11 | DELETE | `/auth/session` | 로그아웃 | 로그인 사용자 |
| API-12 | GET | `/me/profile` | 내 프로필 조회 | 학생 |
| API-13 | PATCH | `/me/profile` | 내 프로필 수정 | 학생 |
| API-14 | POST | `/travel-options` | 막차·동승·혼자 택시 비교 | 학생 |
| API-15 | POST | `/journeys` | 한 번의 귀가 의도 생성 | 학생 |
| API-16 | GET | `/me/journeys` | 내 귀가 기록 조회 | 학생 |
| API-17 | PUT | `/journeys/{journeyId}/choice` | 막차 또는 혼자 택시 선택 기록 | 해당 귀가의 학생 |
| API-18 | GET | `/matching-slots` | 조건별 출발 슬롯·대기 수 조회 | 학생 |
| API-19 | POST | `/journeys/{journeyId}/ride-requests` | 동승 신청·실패 후 재등록 | 해당 귀가의 학생 |
| API-20 | GET | `/ride-requests/{requestId}` | 신청·매칭 결과 조회 | 해당 신청의 학생 |
| API-21 | POST | `/ride-requests/{requestId}/cancel` | 대기 신청 취소 | 해당 신청의 학생 |
| API-22 | GET | `/parties/{partyId}` | 팟·멤버·대표·다음 행동 조회 | 해당 팟 참여자 |
| API-23 | POST | `/parties/{partyId}/confirm` | 본인 팟 확정 | 해당 팟 참여자 |
| API-24 | POST | `/parties/{partyId}/leave` | 팟 거절·이탈 | 해당 팟 참여자 |
| API-25 | POST | `/parties/{partyId}/taxi-call-confirmation` | 대표의 외부 택시 호출 확인 | 해당 팟 대표 |
| API-26 | POST | `/parties/{partyId}/boarding` | 본인 탑승·미탑승 기록 | 해당 팟 참여자 |
| API-27 | GET | `/parties/{partyId}/room` | 팟 채팅방·확인 코드 조회 | 해당 팟 참여자 |
| API-28 | GET | `/rooms/{roomId}/messages` | 채팅 이력·새 메시지 조회 | 해당 방 접근권한을 가진 참여자 |
| API-29 | POST | `/rooms/{roomId}/messages` | 채팅 메시지 전송 | 해당 방 접근권한을 가진 참여자 |
| API-30 | POST | `/rooms/{roomId}/reports` | 멤버·메시지 신고 | 해당 방 접근권한을 가진 참여자 |
| API-31 | PUT | `/rooms/{roomId}/accounts/{purpose}` | 내 선송금·환급 수취 계좌 등록 | 본인 계좌의 팟 참여자 |
| API-32 | GET | `/transfers/{transferId}/recipient-account` | 송금할 수취 계좌 조회 | 해당 이체의 송금자 |
| API-33 | GET | `/parties/{partyId}/settlement` | 예상·선송금·최종 정산 조회 | 해당 팟 참여자 |
| API-34 | POST | `/parties/{partyId}/settlement/receipt` | 대표 결제 화면 첨부 | 해당 팟 대표 |
| API-35 | GET | `/parties/{partyId}/settlement/receipt` | 참여자 결제 화면 열기 | 해당 팟 참여자 |
| API-36 | PATCH | `/parties/{partyId}/settlement/route` | 정산 전 실제 하차 순서 수정 | 해당 팟 대표 |
| API-37 | POST | `/parties/{partyId}/settlement/preview` | 실제 요금 분담·환급 미리보기 | 해당 팟 대표 |
| API-38 | POST | `/parties/{partyId}/settlement/finalize` | 실제 요금·개인 부담·환급 확정 | 해당 팟 대표 |
| API-39 | POST | `/transfers/{transferId}/sent` | 송금자의 ‘보냈어요’ | 해당 이체의 송금자 |
| API-40 | POST | `/transfers/{transferId}/received` | 수취자의 ‘받았어요’ | 해당 이체의 수취자 |
| API-41 | GET | `/me/notifications` | 내 알림 조회 | 로그인 사용자 |
| API-42 | POST | `/me/notifications/{notificationId}/read` | 알림 읽음 처리 | 로그인 사용자 |
| API-43 | GET | `/demand/candidates` | 수요 지도·노선 후보 목록 | 로그인 사용자 |
| API-44 | GET | `/demand/candidates/{estimateId}` | 노선 구간·근거 지표 상세 | 로그인 사용자 |
| API-45 | GET | `/me/demand-contribution` | 내 귀가 반영 횟수·후보 여부 | 학생 |
| API-46 | POST | `/staff/reports` | 후보·조치로 리포트 초안 생성 | 학교 관계자 |
| API-47 | GET | `/staff/reports` | 내 리포트 목록 | 학교 관계자 |
| API-48 | GET | `/staff/reports/{reportId}` | 리포트 미리보기·생성 상태 조회 | 리포트를 만든 관계자 |
| API-49 | POST | `/staff/reports/{reportId}/exports` | 리포트 PDF·이미지 생성 | 리포트를 만든 관계자 |
| API-50 | GET | `/staff/reports/{reportId}/download` | 완성된 리포트 다운로드 URL | 리포트를 만든 관계자 |

## 4. API 상세

공통 오류는 6절을 함께 적용합니다. 응답 모델의 각 필드·필수 여부는 5절에서 정의합니다. 요청 예시의 선택 항목은 DTO 표를 기준으로 생략할 수 있습니다.

### 인증·기준정보

### API-01 · GET /universities · 참여 대학·인증 도메인 조회

**권한:** 비로그인 (`PUBLIC`) · **성공:** 200 · 응답 `data: University[]`

**요청 본문:** 없음

**처리·검증:** 대상은 강남대학교(`kangnam.ac.kr`), 경희대학교(`khu.ac.kr`), 단국대학교(`dankook.ac.kr`), 명지대학교(`mju.ac.kr`), 한국외국어대학교(`hufs.ac.kr`)다. 서버 시작 시 없는 대학·도메인만 활성 상태로 추가하고 기존 항목의 활성 상태나 소속은 변경하지 않는다. 조회에는 대상 중 활성 대학과 해당 대학의 활성 허용 도메인만 반환한다. 대학이 활성이고 도메인이 비활성이면 도메인 목록은 비어 있다. 이미 다른 대학에 연결된 동일 도메인은 자동 수정하지 않는다.

**ERD 연결:** `universities`, `university_domains`

### API-02 · GET /profile-options · 프로필 선택값 조회

**권한:** 비로그인 (`PUBLIC`) · **성공:** 200 · 응답 `data: ProfileOptions`

**요청 본문:** 없음

**처리·검증:** 화면 정의서 v0.3의 여성(`FEMALE`)·남성(`MALE`) 선택지와 `sameGenderDefault: true`를 반환합니다. 동성 매칭 끄기도 허용하며 프런트는 켜기·끄기 토글을 활성화합니다. 응답의 true는 초기 기본값이며 켜짐만 허용한다는 의미가 아닙니다. 가입 시 다른 성별 코드는 422 INVALID_GENDER_CODE입니다.

**업무 오류:** 없음 (공통 오류 계약 적용)

**ERD 연결:** 기준 설정 또는 인증 인프라

### API-03 · GET /banks · 수취 계좌 은행 목록

**권한:** 학생 (`STUDENT`) · **성공:** 200 · 응답 `data: Bank[]`

**요청 본문:** 없음

**처리·검증:** 외부 기준정보/설정 조회. ERD에 새 은행 테이블을 전제하지 않음.

**ERD 연결:** 기준 설정 또는 인증 인프라

### API-04 · GET /pickup-points · 고정 출발 거점 목록

**권한:** 학생 (`STUDENT`) · **성공:** 200 · 응답 `data: PickupPoint[]`

| Query | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `universityId` | string | 아니오 | 학교 거점 필터. 공용 거점도 포함; 형식: `^[1-9][0-9]*$` |

**요청 본문:** 없음

**처리·검증:** 활성 고정 거점만 제공. 좌표는 공개 집결 좌표이고 가까운 순 정렬은 기기 현재 위치로 클라이언트에서 계산 가능.

**ERD 연결:** `pickup_points`, `administrative_areas`

### API-05 · POST /auth/email-verifications · 학교 이메일 인증번호 발급·재발급

**권한:** 비로그인 (`PUBLIC`) · **성공:** 202 · 응답 `data: EmailVerification`

`Idempotency-Key` 필수

**요청 본문:** `EmailVerificationRequest` · application/json

```json
{
  "universityId": "1",
  "email": "student@university.example"
}
```

**처리·검증:** 6자리 코드 유효시간은 발급 시점부터 10분. 인증 정보를 먼저 DB에 커밋한 다음 SMTP 발송 결과를 기다린다. 재발급 시 이전 미사용 코드를 무효화한다. 같은 이메일의 발급 간격은 1분이며 한국 시간 자정부터 하루 10회까지 발급한다. SMTP 실패 또는 결과 불확실도 이미 생성된 발급 건으로 집계한다. 실패한 동일 멱등 요청은 자동 재발송하지 않고 503을 반환하며, 새 요청은 발급 제한을 따른다. 제한 시 Retry-After를 반환한다. 코드 원문·이메일 본문은 로그 제외.

**업무 오류:** `422 EMAIL_DOMAIN_NOT_ALLOWED`, `409 IDEMPOTENCY_CONFLICT`, `410 VERIFICATION_EXPIRED`, `429 RATE_LIMITED`, `503 EMAIL_DELIVERY_UNAVAILABLE`, `503 POLICY_NOT_CONFIGURED`

**ERD 연결:** `email_verifications`, `universities`, `university_domains`

### API-06 · POST /auth/email-verifications/{verificationId}/verify · 이메일 코드 검증

**권한:** 비로그인 (`PUBLIC`) · **성공:** 200 · 응답 `data: VerificationGrant`

경로 `verificationId` 필수 · `Idempotency-Key` 필수

**요청 본문:** `CodeVerificationRequest` · application/json

```json
{
  "code": "123456"
}
```

**처리·검증:** 미소비·미폐기·미만료 코드만 원자적으로 소비. 같은 이메일에서 인증번호를 5회 틀리면 10분 동안 인증 확인을 제한하며 재발급해도 제한은 유지한다. 잘못된 코드를 같은 키와 같은 요청으로 재시도하면 실패 횟수를 다시 늘리지 않고 기존 오류를 반환한다. 같은 키에 다른 인증 ID 또는 코드를 사용하면 409 IDEMPOTENCY_CONFLICT를 반환한다. 이메일·대학을 바인딩한 10분 유효의 1회용 증표를 발급. 인증 성공만으로 STAFF 권한을 얻을 수 없다. 이메일 중복 가입 검증은 학생 가입 API(API-07)에서 수행하며, 기존 계정은 역할·상태에 관계없이 중복 가입을 차단한다. 탈퇴 후 재가입 정책과 이메일 UNIQUE 변경은 별도 작업이다.

**업무 오류:** `422 INVALID_VERIFICATION_CODE`, `404 RESOURCE_NOT_FOUND`, `409 IDEMPOTENCY_CONFLICT`, `410 VERIFICATION_EXPIRED`, `409 VERIFICATION_USED`, `429 RATE_LIMITED`, `503 POLICY_NOT_CONFIGURED`

**ERD 연결:** `email_verifications`

### API-07 · POST /auth/student-registrations · 학생 가입·프로필 생성

**권한:** 비로그인 (`PUBLIC`) · **성공:** 201 · 응답 `data: Session`

`Idempotency-Key` 필수

**요청 본문:** `StudentRegistration` · application/json

```json
{
  "verificationToken": "example-one-time-proof",
  "profile": {
    "realName": "김나연",
    "genderCode": "FEMALE",
    "sameGenderDefault": true,
    "defaultDropoff": {
      "latitude": 37.33,
      "longitude": 127.27
    }
  }
}
```

**처리·검증:** 검증 증표의 대학·이메일로 계정을 생성합니다. 대학·이메일은 요청에서 받지 않고 인증 증표에서 얻으며 활성 대학·도메인인지 다시 검사합니다. `role=STUDENT`, `status=ACTIVE`, `userId`는 서버가 결정합니다. 이름은 앞뒤 공백 제거 후 2~20자, 성별은 `FEMALE`/`MALE`, 동성 매칭은 `true`/`false` 모두 허용하고 선택한 값을 저장합니다(생략/null이면 true). 위도 -90~90, 경도 -180~180의 유한한 숫자가 필수입니다.

**행정동 연결:** develop에 머지된 `AdministrativeAreaResolver`가 `DropoffLocationService.resolveDong(latitude, longitude)`를 구현하여 가입에 주입됩니다. 카카오의 행정동(H) 공식 코드로 DB를 조회하고, 없는 시·구·동은 공통 서비스가 등록하여 DONG 엔티티를 반환합니다. 지역 판별 실패는 422 LOCATION_UNRESOLVABLE, 키 누락·카카오 장애는 503 UPSTREAM_UNAVAILABLE로 전달합니다. 가입 트랜잭션 실패 시 증표 소비·계정·프로필·새 지역 저장도 함께 롤백됩니다.

인증 증표 소비·계정·암호화한 실명/하차 좌표·프로필·가입 멱등 기록은 한 트랜잭션입니다. 위치 판별이나 저장 실패 시 증표 소비도 롤백되어 다시 시도할 수 있습니다. 기존 계정은 역할·상태에 관계없이 이메일 중복 가입을 막습니다. 탈퇴 후 재가입 정책과 이메일 UNIQUE 변경은 별도 작업입니다.

`Idempotency-Key`는 UUID가 필수입니다. 같은 키·같은 정규화 요청은 기존 계정으로 201을 반환하고 세션을 새로 발급하며, 같은 키·다른 요청은 409 IDEMPOTENCY_CONFLICT입니다. 원래 인증 증표 만료까지 재시도가 가능하고, 만료 뒤에는 세션을 재발급하지 않습니다. 계정 정지·탈퇴 시 재시도 세션 발급도 막습니다. 토큰·실명·좌표 원문 대신 비밀키 기반 요청 해시와 계정 ID·만료만 인프라 테이블 `student_registration_receipts`에 보관하고, 만료 기록은 주기적으로 삭제합니다. 이미 만료 기록이 정리된 뒤의 증표 재사용은 VERIFICATION_USED 또는 VERIFICATION_EXPIRED입니다.

**인증 연결:** DB 커밋 성공 후 서버 세션 쿠키를 발급하고 201 `data: Session`을 반환합니다. `data.user.universityId`를 유지하면서 `data.user.universityName`을 추가했습니다. 로그인/재시도 시 세션 ID와 CSRF 토큰은 새로 생성하며 응답은 `Cache-Control: no-store`입니다. 가입 응답은 세션 DTO이므로 하차 동 이름은 포함하지 않습니다. 가입 후 프로필 조회 API-12에서 `defaultDongId`와 `defaultDongName`을 함께 받습니다.

**업무 오류:** `410 VERIFICATION_EXPIRED`, `409 VERIFICATION_USED`, `409 ACCOUNT_ALREADY_EXISTS`, `409 IDEMPOTENCY_CONFLICT`, `422 EMAIL_DOMAIN_NOT_ALLOWED`, `422 INVALID_GENDER_CODE`, `422 LOCATION_UNRESOLVABLE`, `422 VALIDATION_ERROR`, `403 FORBIDDEN`, `403 ACCOUNT_SUSPENDED`, `403 ACCOUNT_UNAVAILABLE`, `503 POLICY_NOT_CONFIGURED`, `503 UPSTREAM_UNAVAILABLE`

**ERD 연결:** `users`, `student_profiles`, `email_verifications`, `administrative_areas`

### API-08 · POST /auth/student-sessions · 학생 재로그인

**권한:** 비로그인 (`PUBLIC`) · **성공:** 200 · 응답 `data: Session`

`Idempotency-Key` 필수

**요청 본문:** `StudentLogin` · application/json

```json
{
  "verificationToken": "example-one-time-proof"
}
```

**처리·검증:** 기존 이메일 발급·검증 API로 학교 이메일을 재인증하고 받은 `verificationToken`을 전달합니다. 증표의 이메일에 해당하는 기존 계정과 대학 일치를 확인하고, STUDENT/ACTIVE 및 학생 프로필 존재를 검증합니다. 계정·프로필을 생성하거나 수정하지 않습니다. STAFF·탈퇴·프로필 누락·대학 불일치는 403 ACCOUNT_UNAVAILABLE, 정지는 403 ACCOUNT_SUSPENDED입니다. 정지 계정용 제한 로그인은 이번 구현에 포함하지 않고 기존 공통 세션 발급 정책을 유지합니다.

**트랜잭션·재시도:** 증표 소비와 `student_login_receipts`의 로그인 멱등 기록은 같은 트랜잭션입니다. 실패하면 증표 소비도 롤백합니다. UUID Idempotency-Key를 정규화하고 같은 키·같은 증표는 기존 계정으로 세션을 재발급하며, 같은 키·다른 증표는 409 IDEMPOTENCY_CONFLICT입니다. 최초 증표 만료까지만 재시도하고 매번 계정 역할·상태·대학·프로필을 다시 확인합니다. 다른 키나 가입 API에서 이미 소비된 증표를 재사용하면 409 VERIFICATION_USED입니다. 만료 기록은 주기적으로 정리하며, 정리 전 만료 재시도는 410, 정리 후 소비된 증표 재사용은 409입니다. 원문 대신 요청 HMAC·사용자/대학 ID·만료만 저장합니다.

**인증 연결:** 커밋 성공 후 SessionService로 세션·CSRF 토큰을 발급하고 HTTP 200 `data: Session`(대학 ID·대학명 포함), `Cache-Control: no-store`를 반환합니다. 공개 POST의 허용 Origin이 필수입니다. 비로그인에는 CSRF 토큰이 필요 없으며, 기존 인증 세션 쿠키를 함께 보내면 X-CSRF-Token도 필요합니다. 성공 시 세션 ID와 CSRF 토큰을 교체합니다. 세션·쿠키 유지 기간은 공통 설정 30일입니다. 기존 이메일 발송·인증 실패 제한은 그대로 적용하고, 로그인 API 자체의 추가 속도 제한 수치는 이번에 새로 정하지 않습니다.

**프런트 연결:** 유효한 세션이면 GET /auth/session 확인 후 홈으로 이동합니다. 세션이 없으면 이메일 인증 → 이 API 호출 → 성공 시 홈, 404 ACCOUNT_NOT_FOUND 시 유효 증표를 유지하고 프로필 가입으로 진행합니다. 이 기존/신규 학생 분기는 API 구현 기준이며 화면 정의서의 '이미 가입한 메일' 안내 및 인증 후 이동 문구와 프런트 연동 시 맞춰야 합니다.

**업무 오류:** `404 ACCOUNT_NOT_FOUND`, `410 VERIFICATION_EXPIRED`, `409 VERIFICATION_USED`, `409 IDEMPOTENCY_CONFLICT`, `422 VALIDATION_ERROR`, `403 FORBIDDEN`, `403 ACCOUNT_SUSPENDED`, `403 ACCOUNT_UNAVAILABLE`, `503 POLICY_NOT_CONFIGURED` 및 공통 오류

**ERD 연결:** `users`, `email_verifications`

### API-09 · POST /auth/staff-sessions · 관계자 로그인

**권한:** 비로그인 (`PUBLIC`) · **성공:** 200 · 응답 `data: Session`

`Idempotency-Key` 필수

**요청 본문:** `StaffLogin` · application/json

```json
{
  "email": "staff@university.example",
  "password": "example-password-only"
}
```

**처리·검증:** 운영팀이 발급한 기존 STAFF 계정의 BCrypt password_hash를 검증합니다. 이메일은 앞뒤 공백 제거·소문자로 정규화하고 비밀번호는 공백을 포함해 그대로 검증합니다. 두 필드는 JSON 문자열만 허용합니다. 이메일은 최대 255자, 비밀번호는 비어 있지 않은 UTF-8 72바이트 이하입니다. 계정 없음·STUDENT 계정·비밀번호 오류·비밀번호 해시 누락/형식 오류는 모두 401 INVALID_CREDENTIALS("메일이나 비밀번호가 맞지 않아요.")입니다. 계정 부재 시에도 더미 BCrypt 검증을 수행합니다. 학생 이메일 OTP·도메인 제한·학생 프로필은 요구하지 않습니다.

**상태·권한:** 올바른 비밀번호를 확인한 뒤 탈퇴·참조 대학 없음은 403 ACCOUNT_UNAVAILABLE, 정지는 403 ACCOUNT_SUSPENDED로 차단합니다. 제한 세션 발급 제안은 적용하지 않고 기존 공통 세션 발급 정책을 유지합니다. STAFF는 수요/관계자 경로만 역할에 맞게 접근하고, 학생 개인정보·귀가·동승·채팅·송금 경로는 공통 인증 설정에서 403으로 차단합니다.

**실패 잠금:** 정규화 이메일의 HMAC을 기준으로 연속 실패를 DB에 저장합니다. 기본 5번째 새 실패부터 30분 잠금(429 RATE_LIMITED + 남은 초의 Retry-After)이며 잠금 중 올바른 비밀번호도 차단합니다. 잠금 요청이 기존 잠금 만료를 연장하지 않습니다. 성공하면 실패 횟수를 초기화하고, 30분이 지나면 자동 해제합니다. 존재하지 않는 이메일에도 동일한 실패·잠금 계약을 적용합니다. 업무 계정의 SUSPENDED와 별개인 인증용 잠금입니다.

**재시도·트랜잭션:** UUID Idempotency-Key가 필수입니다. 같은 키·같은 정규화 요청의 인증 실패 재전송은 실패 횟수를 중복 집계하지 않고, 같은 키·다른 요청은 409 IDEMPOTENCY_CONFLICT입니다. 성공 재시도는 현재 비밀번호·역할·상태·잠금·계정/대학 바인딩을 다시 검사합니다. 잠금은 이전 성공 결과보다 우선하며, 비밀번호 변경 후 이전 성공 기록만으로 세션을 발급하지 않습니다. 실패·멱등 기록 커밋 후 오류를 반환하고 예상 못 한 저장 실패는 롤백합니다. 성공 커밋 뒤 SessionService로 세션·CSRF 토큰을 발급하며 기존 세션 ID도 교체합니다.

**기록 설정:** 피그마가 정한 값은 5회·30분 잠금입니다. 구현 상세로 재시도 기록 기본 유효기간도 최초 요청부터 30분(설정 가능)이며 성공 재시도로 연장하지 않습니다. 만료·정리 후 같은 키가 다시 오면 현재 자격을 검증하는 새 요청으로 처리합니다. 비밀번호·이메일 원문·비밀번호 해시·세션/CSRF 토큰은 재시도 저장소에 저장하지 않습니다. `staff_login_attempts`는 계정 HMAC·실패 횟수·잠금·최근 접근, `staff_login_receipts`는 키·요청 HMAC·성공 계정/대학 ID 또는 오류·만료만 저장합니다. 만료 멱등 기록과 30일 미사용·잠금 해제된 실패 기록을 주기적으로 정리합니다.

**응답·프런트:** 200 + 기존 Session(대학명 포함), Cache-Control: no-store, 30일 세션 쿠키를 사용합니다. 허용 Origin이 필수이고 비로그인에는 CSRF 토큰이 필요 없으며, 기존 인증 쿠키를 함께 보내면 X-CSRF-Token도 필요합니다. 성공하면 A-01 수요 지도로 이동합니다. 401은 동일 문구로 안내하고 비밀번호 입력을 지우며 새 시도에는 새 UUID를 사용합니다. 429는 Retry-After 동안 재시도를 제한합니다. API 경로·요청 필드·성공 응답 구조는 원본 명세를 유지합니다.

**업무 오류:** `401 INVALID_CREDENTIALS`, `429 RATE_LIMITED`, `403 ACCOUNT_SUSPENDED`, `403 ACCOUNT_UNAVAILABLE`, `409 IDEMPOTENCY_CONFLICT`, `422 VALIDATION_ERROR`, `403 FORBIDDEN`, `503 POLICY_NOT_CONFIGURED` 및 공통 오류

**ERD 연결:** `users`

### API-10 · GET /auth/session · 현재 계정·세션 확인

**권한:** 로그인 사용자 (`AUTHENTICATED`) · **성공:** 200 · 응답 `data: Session`

**요청 본문:** 없음

**처리·검증:** 현재 사용자와 세션 만료·CSRF 증표만 반환. 세션 원문은 응답 body에 노출하지 않음.

`Cache-Control: no-store`를 적용합니다. `expiresAt`은 유휴 만료 시각으로 요청 시 갱신됩니다. 세션 없음/만료는 401 UNAUTHENTICATED입니다.

**ERD 연결:** `users`

### API-11 · DELETE /auth/session · 로그아웃

**권한:** 로그인 사용자 (`AUTHENTICATED`) · **성공:** 204 · 응답 본문 없음

`Idempotency-Key` 필수

**요청 본문:** 없음

**처리·검증:** 현재 세션 폐기와 쿠키 만료. 회원 탈퇴·프로필 삭제가 아님.

**재시도 예외:** 이미 세션이 없거나 만료된 경우에도 유효한 UUID `Idempotency-Key`가 있으면 204입니다. 활성 로그인 세션은 CSRF 검증 후 폐기합니다. 동일 명령 자체가 멱등이므로 별도 로그아웃 결과 캐시를 만들지 않습니다. 응답은 `Cache-Control: no-store`입니다.

**ERD 연결:** 기준 설정 또는 인증 인프라

### 프로필·귀가

### API-12 · GET /me/profile · 내 프로필 조회

**권한:** 학생 (`STUDENT`) · **성공:** 200 · 응답 `data: StudentProfile`

성공 응답 `ETag`

**요청 본문:** 없음

**처리·검증:** 본인 실명·기본 하차 핀만 복호화하여 반환. 타인 userId를 입력받지 않음.

`data.user`에는 기존 공통 User의 `universityName`을 포함하고, 기본 하차 행정동은 `defaultDongId`와 `defaultDongName`을 함께 반환합니다. 성별 코드는 가입 시 확정한 FEMALE/MALE입니다. 본인 실명·정확한 기본 좌표를 반환하므로 `Cache-Control: no-store`를 적용합니다. 응답의 `ETag`를 다음 프로필 수정의 `If-Match`에 전달합니다.

**업무 오류:** `401 UNAUTHENTICATED`, `403 FORBIDDEN`, `403 ACCOUNT_SUSPENDED`, `403 ACCOUNT_UNAVAILABLE`, `404 RESOURCE_NOT_FOUND` 및 공통 오류

**ERD 연결:** `users`, `student_profiles`

### API-13 · PATCH /me/profile · 내 프로필 수정

**권한:** 학생 (`STUDENT`) · **성공:** 200 · 응답 `data: StudentProfile`

`Idempotency-Key` 필수 · `If-Match` 필수 · 성공 응답 `ETag`

**요청 본문:** `ProfilePatch` · application/json

```json
{
  "sameGenderDefault": true,
  "defaultDropoff": {
    "latitude": 37.33,
    "longitude": 127.27
  }
}
```

**처리·검증:** 화면 정의서 v0.4 S-08과 사용자 확인에 따라 동성 매칭 기본값과 기본 하차 지점만 수정합니다. 이름·학교·이메일·성별은 조회 전용이며 요청에 포함하면 422입니다. 재인증을 통한 이름·학교 변경 화면은 정의되지 않아 이번 API에 포함하지 않습니다. 위치·알림 권한 상태는 기기/브라우저가 관리하며 프로필 DB에 저장하지 않습니다.

**부분 수정:** `sameGenderDefault`는 JSON boolean true/false 모두 허용합니다. `defaultDropoff`는 숫자형 latitude(-90~90)·longitude(-180~180)를 함께 보냅니다. 최소 한 필드가 필요하며 생략한 필드는 유지합니다. 명시적인 null, 빈 객체, 숫자·boolean의 문자열 대체, 미정의 필드는 422입니다.

**위치 저장:** 공통 DropoffLocationService로 좌표를 행정동에 연결하고, 동 ID·암호화한 좌표·토글 변경·멱등 기록을 같은 트랜잭션에서 커밋합니다. 판별·암호화·저장 실패 시 모두 롤백합니다. 카카오 키 누락·장애는 503 UPSTREAM_UNAVAILABLE, 판별 불가는 422 LOCATION_UNRESOLVABLE입니다. 프로필 변경은 기존 귀가·동승 신청의 행정동·좌표·동성 옵션·성별 스냅샷에 소급하지 않습니다. S-08에서 지도에 진입한 경우에만 이 API로 기본값을 저장하며, S-02 동승 등록에서 고른 위치는 해당 신청에 사용합니다.

**동시 수정·재시도:** 로그인 쿠키와 X-CSRF-Token, UUID Idempotency-Key, If-Match가 필수입니다. 새 명령의 If-Match 누락은 428, 현재 프로필 응답과 불일치는 412입니다. 같은 키·같은 사용자·같은 요청의 완료 기록은 ETag 비교 전에 확인해 재적용 없이 현재 프로필·ETag를 반환합니다. 이후 다른 수정이 있었다면 그 현재 값을 반환하며 과거 값으로 되돌리지 않습니다. 같은 키의 다른 사용자/본문은 409입니다. 실패한 수정에는 완료 기록을 남기지 않아 같은 키·본문으로 재시도할 수 있습니다. 계정 상태·역할 검증은 재시도에도 적용합니다.

**멱등 기록:** `student_profile_update_receipts`에 키·사용자 ID·요청 HMAC·만료만 보관하며 실명·좌표·응답·세션 원문은 저장하지 않습니다. 기존 VERIFICATION_SECRET(32바이트 이상)을 재사용합니다. 기록의 구현 기본 유효기간은 성공 커밋 기준 24시간(PROFILE_UPDATE_RECEIPT_TTL_SECONDS=86400, 생략 가능)이며 재시도로 연장하지 않습니다. 만료·삭제된 키는 현재 ETag가 필요한 새 요청으로 처리합니다. 행 잠금과 FOR UPDATE SKIP LOCKED로 수정 중 기록이 정리에 삭제되지 않게 합니다. 이 보관 기간은 구현 설정이며 피그마의 별도 정책은 아닙니다.

**응답:** 200 + 현재 StudentProfile, 새 ETag, Cache-Control: no-store. Swagger의 PATCH에는 X-CSRF-Token 입력란도 제공합니다.

**업무 오류:** `401 UNAUTHENTICATED`, `403 FORBIDDEN`, `403 ACCOUNT_SUSPENDED`, `403 ACCOUNT_UNAVAILABLE`, `404 RESOURCE_NOT_FOUND`, `409 IDEMPOTENCY_CONFLICT`, `412 PRECONDITION_FAILED`, `428 PRECONDITION_REQUIRED`, `422 VALIDATION_ERROR`, `422 LOCATION_UNRESOLVABLE`, `503 UPSTREAM_UNAVAILABLE`, `503 POLICY_NOT_CONFIGURED` 및 공통 오류

**ERD 연결:** `student_profiles`, `administrative_areas`

### API-14 · POST /travel-options · 막차·동승·혼자 택시 비교

**권한:** 학생 (`STUDENT`) · **성공:** 200 · 응답 `data: TravelOptions`

**요청 본문:** `TravelOptionsRequest` · application/json

```json
{
  "pickupPointId": "11",
  "dropoff": {
    "latitude": 37.33,
    "longitude": 127.27
  },
  "departureAt": "2026-11-05T23:30:00+09:00",
  "currentLocation": {
    "latitude": 37.331,
    "longitude": 127.271
  }
}
```

**처리·검증:** 현재 GPS는 일시 계산만 하고 DB·접근 로그·분석 이벤트·오류 로그·캐시 키에 남기지 않음. 선택한 하차 핀도 이 API에서는 저장하지 않음. 외부자료 부족 시 해당 값 NULL 및 warnings. 모든 값은 추정.

**업무 오류:** `422 LOCATION_UNRESOLVABLE`, `503 UPSTREAM_UNAVAILABLE`

**ERD 연결:** `pickup_points`, `student_profiles`, `ride_requests`

### API-15 · POST /journeys · 한 번의 귀가 의도 생성

**권한:** 학생 (`STUDENT`) · **성공:** 201 · 응답 `data: Journey`

`Idempotency-Key` 필수

**요청 본문:** `JourneyCreate` · application/json

```json
{
  "clientJourneyKey": "0b18e222-668f-4b29-a013-6f1169e7a001",
  "pickupPointId": "11",
  "dropoff": {
    "latitude": 37.33,
    "longitude": 127.27
  },
  "intendedDepartureAt": "2026-11-05T23:30:00+09:00"
}
```

**처리·검증:** 학교는 로그인 사용자 기준 스냅샷. 핀에서 동을 판별해 동만 journeys에 저장하고 원 좌표는 이 API에서 지속 저장하지 않음. 결과는 UNRESOLVED. 등록 재시도는 같은 clientJourneyKey를 사용. 거점이 없거나 비활성이거나 다른 학교 전용이면 404 `RESOURCE_NOT_FOUND`(공용 거점은 모든 학교 학생이 사용). 같은 학생·같은 clientJourneyKey·같은 요청(거점·하차 동·귀가 예정 시각)을 다시 보내면 먼저 만든 귀가를 201로 반환하고, 같은 키의 다른 요청은 409 `IDEMPOTENCY_CONFLICT`. `ACTIVE_JOURNEY_CONFLICT`는 다른 귀가에 진행 중인 동승이 있을 때: 대기(WAITING) 신청이 있거나, 매칭(MATCHED)됐고 그 팟이 아직 끝나지 않은(COMPLETED·CANCELLED가 아닌) 경우. 신청이 없는 UNRESOLVED 귀가는 막지 않는다.

**업무 오류:** `404 RESOURCE_NOT_FOUND`, `409 ACTIVE_JOURNEY_CONFLICT`, `422 LOCATION_UNRESOLVABLE`, `503 UPSTREAM_UNAVAILABLE`, `409 IDEMPOTENCY_CONFLICT`

**ERD 연결:** `journeys`, `users`, `administrative_areas`

### API-16 · GET /me/journeys · 내 귀가 기록 조회

**권한:** 학생 (`STUDENT`) · **성공:** 200 · 응답 `data: Journey[]`

| Query | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `cursor` | string | 아니오 | 다음 페이지 커서 |
| `limit` | integer | 아니오 | 페이지 크기; 최소 1; 최대 100; 기본 20 |

**요청 본문:** 없음

**처리·검증:** 본인 귀가만 반환. 매칭 실패·재등록 횟수와 최종 선택은 별도 해석; 귀가 건수를 신청 횟수로 늘리지 않음.

**ERD 연결:** `journeys`, `ride_requests`

### API-17 · PUT /journeys/{journeyId}/choice · 막차 또는 혼자 택시 선택 기록

**권한:** 해당 귀가의 학생 (`OWNER_JOURNEY`) · **성공:** 200 · 응답 `data: Journey`

경로 `journeyId` 필수 · `Idempotency-Key` 필수

**요청 본문:** `JourneyChoice` · application/json

```json
{
  "mode": "BUS"
}
```

**처리·검증:** 기존 대기 신청이 있으면 같은 트랜잭션에서 CANCELLED 처리 후 선택 기록. 이미 매칭된 팟은 먼저 허용된 팟 이탈 절차가 필요. POOL_BOARDED는 이 API로 입력 불가. 동일 선택 재전송은 현재 결과 반환; 다른 최종 선택으로 덮어쓰기 금지. 취소로 journey가 CANCELLED인 경우 선택 기록 불가. 본인 귀가가 아니거나 없는 귀가는 둘 다 404 `RESOURCE_NOT_FOUND`. 이미 동승 탑승(POOL_BOARDED)·다른 선택이 기록됐거나 CANCELLED인 귀가는 `JOURNEY_ALREADY_RESOLVED`. 선택이 기록되면 활성 신청이 없어 응답의 `activeRequestId`는 null.

**업무 오류:** `404 RESOURCE_NOT_FOUND`, `409 PARTY_LEAVE_REQUIRED`, `409 JOURNEY_ALREADY_RESOLVED`

**ERD 연결:** `journeys`, `ride_requests`

### 동승·매칭

### API-18 · GET /matching-slots · 조건별 출발 슬롯·대기 수 조회

**권한:** 학생 (`STUDENT`) · **성공:** 200 · 응답 `data: MatchingSlot[]`

| Query | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `pickupPointId` | string | 예 | 고정 거점; 형식: `^[1-9][0-9]*$` |
| `destinationDongId` | string | 예 | 도착 동; 권역은 서버가 판별; 형식: `^[1-9][0-9]*$` |
| `date` | string (date) | 예 | 출발의 실제 현지 날짜 |
| `sameGenderOnly` | boolean | 예 | 이번 신청 동성 조건 |

**요청 본문:** 없음

**처리·검증:** 로그인 학교·성별·차단 관계로 본인과 호환되는 WAITING 신청만 계산하고 본인 신청 제외. ‘구간 등록 5건 미만 숨김’은 수요 지도 규칙이며 슬롯 대기 수와 구분. `date`(한국 날짜)에 있는 출발 시간대를 모두 돌려준다: 22:00~02:00, 30분 단위(화면정의서 공통 정책), 자정을 넘기는 시간대는 실제 날짜로 센다(예: 11.6이면 00:00~02:00과 22:00~23:30). `waitingCount`는 같은 학교·거점·도착 구의 WAITING 신청 중 성별 조건이 서로 맞고(한쪽이라도 동성만 원하면 성별이 같아야 함) 나와 차단한 사이가 아닌 신청만 센다. 출발 15분 전이 지난 시간대도 목록에 있고 `registrationAllowed=false`, `reason=REGISTRATION_CLOSED`(숨길지는 화면이 정함). 등록할 수 있으면 `reason`은 null. 거점·도착 동이 없거나 내 학교가 쓸 수 없으면 404, 학생이 아니면 403.

**업무 오류:** `404 RESOURCE_NOT_FOUND`, `403 FORBIDDEN`, `503 POLICY_NOT_CONFIGURED`(서비스 시간은 기본값 22:00~02:00으로 설정돼 있어 현재 발생하지 않음)

**ERD 연결:** `ride_requests`, `users`, `student_profiles`, `user_blocks`

### API-19 · POST /journeys/{journeyId}/ride-requests · 동승 신청·실패 후 재등록

**권한:** 해당 귀가의 학생 (`OWNER_JOURNEY`) · **성공:** 201 · 응답 `data: RideRequest`

경로 `journeyId` 필수 · `Idempotency-Key` 필수

**요청 본문:** `RideRequestCreate` · application/json

```json
{
  "clientRequestKey": "0b18e222-668f-4b29-a013-6f1169e7a002",
  "pickupPointId": "11",
  "dropoff": {
    "latitude": 37.33,
    "longitude": 127.27
  },
  "departureAt": "2026-11-05T23:30:00+09:00",
  "sameGenderOnly": true
}
```

**처리·검증:** 귀가는 UNRESOLVED여야 함. 본인 활성 신청은 전체 귀가에 걸쳐 최대 1개. 서버 허용 슬롯, 소속, 권역, 동성 조건을 검증하고 핀·성별을 신청에 스냅샷 저장. 재등록은 같은 journeyId에 새 request 행 생성; journey 집계 거점·동·시각을 이번 선택으로 갱신. 금액·대기순서는 서버 계산. 출발 시각은 22:00~02:00의 30분 단위 정각이어야 하고 출발 15분 전까지만 등록할 수 있다(아니면 422 `INVALID_DEPARTURE_SLOT`). 하차 핀은 행정동·구로 판별해 신청에 적고(판별하지 못하면 422 `LOCATION_UNRESOLVABLE`) 좌표는 암호화해 저장한다. 성별은 가입한 프로필 값을 스냅샷으로 남긴다. 결과 안내 시각(`resultDueAt`)은 막차 정보 연동 전까지 출발 20분 전(막차 후 등록 기준)으로 둔다. 같은 clientRequestKey·같은 귀가·같은 내용의 재시도는 먼저 만든 신청을 201로 반환하고, 같은 키의 다른 내용은 409 `IDEMPOTENCY_CONFLICT`. `ACTIVE_REQUEST_EXISTS`는 이 학생에게 진행 중인 동승(대기 신청, 또는 매칭됐고 팟이 아직 끝나지 않은 경우)이 있을 때. 귀가나 거점을 쓸 수 없으면 404, 학생이 아니면 403. `UNSETTLED_RIDE_RESTRICTION`은 정산이 앱 밖에서 이뤄지고 자동 제재를 MVP에서 제외했으므로(기획 답변 1·5) 적용하지 않는다.

**업무 오류:** `404 RESOURCE_NOT_FOUND`, `403 FORBIDDEN`, `409 ACTIVE_REQUEST_EXISTS`, `409 JOURNEY_ALREADY_RESOLVED`, `409 IDEMPOTENCY_CONFLICT`, `422 INVALID_DEPARTURE_SLOT`, `422 LOCATION_UNRESOLVABLE`, `409 UNSETTLED_RIDE_RESTRICTION`(MVP 미적용), `503 POLICY_NOT_CONFIGURED`

**ERD 연결:** `journeys`, `ride_requests`, `student_profiles`, `pickup_points`, `administrative_areas`

### API-20 · GET /ride-requests/{requestId} · 신청·매칭 결과 조회

**권한:** 해당 신청의 학생 (`OWNER_REQUEST`) · **성공:** 200 · 응답 `data: RideRequest`

경로 `requestId` 필수 · 성공 응답 `ETag`

**요청 본문:** 없음

**처리·검증:** 본인 신청만 조회. MATCHED일 때 partyId로 결과 화면 이동; FAILED이면 travel-options/다음 슬롯 안내. 결과 조회는 폴링 가능.

**ERD 연결:** `ride_requests`, `party_members`

### API-21 · POST /ride-requests/{requestId}/cancel · 대기 신청 취소

**권한:** 해당 신청의 학생 (`OWNER_REQUEST`) · **성공:** 200 · 응답 `data: RideRequest`

경로 `requestId` 필수 · `Idempotency-Key` 필수

**요청 본문:** 없음

**처리·검증:** WAITING→CANCELLED. 출발 15분 전까지만 취소할 수 있고 지났으면 409 `CANCELLATION_DEADLINE_PASSED`(화면정의서 v0.3.1 S-03 "출발 15분 전까지는 언제든 취소할 수 있어요", 방 나가기 마감과 같은 시각). 이미 CANCELLED면 마감이 지난 뒤에도 동일 응답. MATCHED 이후 취소는 party leave로 처리. 매칭 작업과 같은 신청 잠금을 사용하여 이중 배정을 막음.

**업무 오류:** `409 REQUEST_ALREADY_MATCHED`, `409 INVALID_STATE`, `409 CANCELLATION_DEADLINE_PASSED`

**ERD 연결:** `ride_requests`

### API-22 · GET /parties/{partyId} · 팟·멤버·대표·다음 행동 조회

**권한:** 해당 팟 참여자 (`PARTY_MEMBER`) · **성공:** 200 · 응답 `data: Party`

경로 `partyId` 필수 · 성공 응답 `ETag`

**요청 본문:** 없음

**처리·검증:** 멤버에게는 이름 일부·학교·동·동성 여부만 반환. 정확한 핀/실명/계좌/다른 사용자의 계정 ID는 제외. 참여가 종료된 사용자의 접근 범위는 본인 정산 처리에 필요한 정보로 제한.

**ERD 연결:** `ride_parties`, `party_members`, `party_stops`, `ride_requests`, `student_profiles`, `chat_rooms`

### API-23 · POST /parties/{partyId}/confirm · 본인 팟 확정

**권한:** 해당 팟 참여자 (`PARTY_MEMBER`) · **성공:** 200 · 응답 `data: Party`

경로 `partyId` 필수 · `Idempotency-Key` 필수 · `If-Match` 필수 · 성공 응답 `ETag`

**요청 본문:** 없음

**처리·검증:** 본인 decision PENDING→ACCEPTED. 확정 마감 전, 계정 정상일 때만 가능. 이미 ACCEPTED면 같은 응답. 확정한 사람부터 바로 채팅방에 들어간다: 확정할 때마다 방을 열어 두고(이미 열려 있으면 그대로) 그 멤버가 방에 접근할 수 있게 한다(기획 답변 v2 #9, 화면정의서 v0.3.1 S-05; 대표는 계좌 등록 시점에 입장). 남은 멤버(PENDING·ACCEPTED)가 전원 확정하면 팟을 PREPAYING으로 변경. 확정 마감까지 확정하지 않은 멤버는 팟에서 빠지고 대표가 확정하지 않으면 팟이 해산되는 처리는 서버 내부 작업(확정 마감 처리, 1분마다)이 담당한다. 빠진 멤버의 decision은 DECLINED, 신청은 FAILED이며 따로 알림은 보내지 않는다(화면정의서에 문구 없음). 해산이면 확정한 사람에게 "대표가 빠져서 이번 동승이 취소됐어요"(대표 미확정) 또는 "이번엔 매칭되지 않았어요"(확정 인원 부족) 알림.

**업무 오류:** `409 CONFIRMATION_DEADLINE_PASSED`, `409 INVALID_STATE`, `503 POLICY_NOT_CONFIGURED`

**ERD 연결:** `party_members`, `ride_parties`, `chat_rooms`, `settlements`, `settlement_items`

### API-24 · POST /parties/{partyId}/leave · 팟 거절·이탈

**권한:** 해당 팟 참여자 (`PARTY_MEMBER`) · **성공:** 200 · 응답 `data: Party`

경로 `partyId` 필수 · `Idempotency-Key` 필수 · `If-Match` 필수 · 성공 응답 `ETag`

**요청 본문:** 없음

**처리·검증:** 출발 15분 전까지만 허용(now <= departureAt-15m). PENDING이면 DECLINED, ACCEPTED이면 LEFT. 관련 신청은 CANCELLED로 종료. 이미 빠졌으면 같은 응답. 본인이 보냈거나 받은 SENT/RECEIVED 송금이 있으면 자동 처리 금지(409 FUNDS_ALREADY_IN_FLIGHT). 대표가 빠지거나 남은 인원이 1명이면 팟 해산(CANCELLED), 남은 사람 신청은 FAILED로 바꾸고 매칭 실패 알림(대표 승계 없음, 기획 답변·화면정의서 v0.3 S-05). 2명 이상 남으면 기존 하차 순서에서 빠진 사람만 지워 경로를 재계산하고 route_version 증가(대표 유지). 예상금액 재계산은 정산 쪽 예상 정산 구현 후 연결.

**업무 오류:** `409 CANCELLATION_DEADLINE_PASSED`, `409 FUNDS_ALREADY_IN_FLIGHT`, `503 POLICY_NOT_CONFIGURED`

**ERD 연결:** `party_members`, `ride_requests`, `ride_parties`, `party_stops`, `settlements`, `transfers`

### API-25 · POST /parties/{partyId}/taxi-call-confirmation · 대표의 외부 택시 호출 확인

**권한:** 해당 팟 대표 (`REPRESENTATIVE`) · **성공:** 200 · 응답 `data: Party`

경로 `partyId` 필수 · `Idempotency-Key` 필수 · `If-Match` 필수 · 성공 응답 `ETag`

**요청 본문:** 없음

**처리·검증:** 대표만 가능(대표가 아닌 참여자는 403 FORBIDDEN). 팟이 PREPAYING일 때만 가능(아니면 409 INVALID_STATE). taxi_called_at을 한 번 기록하고 팟을 READY로 변경, 이미 기록했으면 같은 응답. 확정한 일반 멤버에게 알림(제목 "{대표 이름}님이 택시를 불렀어요", 본문 "타면 채팅방에서 탔어요를 눌러 주세요"; 기획 답변 v2 #13). 선송금 수령 여부는 확인하지 않음(입금 확인은 대표가 은행 앱에서 직접, 기획 답변 5·화면정의서 v0.3 S-06 대표 · 받을 금액 확인). 외부 택시 호출 API를 실행하거나 호출 사실을 자동 검증하지 않음. 채팅방 "택시를 불렀어요" 시스템 메시지는 채팅 쪽에서 추가 예정.

**업무 오류:** `403 FORBIDDEN`, `409 INVALID_STATE`

**ERD 연결:** `ride_parties`, `notifications`

### API-26 · POST /parties/{partyId}/boarding · 본인 탑승·미탑승 기록

**권한:** 해당 팟 참여자 (`PARTY_MEMBER`) · **성공:** 200 · 응답 `data: Party`

경로 `partyId` 필수 · `Idempotency-Key` 필수 · 성공 응답 `ETag`

**요청 본문:** `BoardingRequest` · application/json

```json
{
  "status": "BOARDED"
}
```

**처리·검증:** 본인 참여가 ACCEPTED이고, 팟이 PREPAYING/READY/RIDING이고, 탑승 확인 시간(출발 15분 전부터 출발 1시간 뒤까지, 화면정의서 v0.3 S-06)일 때만 기록. 아니면 409 BOARDING_NOT_ALLOWED. BOARDED면 journey.outcome=POOL_BOARDED(outcomeSource=BOARDING_CONFIRMATION)이고 팟을 RIDING으로 전환. MISSED는 사실만 기록하며 자동 전액 환급·제재를 실행하지 않음(정산은 전원 탄 것으로 계산, 기획 답변). 같은 값 재전송은 같은 응답, 다른 값으로 변경은 409 BOARDING_ALREADY_RECORDED. 실제 정산은 하차 후 대표 동작으로 진행한다.

**업무 오류:** `409 BOARDING_NOT_ALLOWED`, `409 BOARDING_ALREADY_RECORDED`, `503 POLICY_NOT_CONFIGURED`

**ERD 연결:** `party_members`, `ride_parties`, `journeys`

### 채팅·신고

### API-27 · GET /parties/{partyId}/room · 팟 채팅방·확인 코드 조회

**권한:** 해당 팟 참여자 (`PARTY_MEMBER`) · **성공:** 200 · 응답 `data: Room`

경로 `partyId` 필수

**요청 본문:** 없음

**처리·검증:** 화면 정의서 v0.4 S-05·S-06에 따라 첫 멤버 확정 시 팟당 방을 하나 생성하고, 전원 확정을 기다리지 않고 ACCEPTED 멤버부터 입장합니다. 방이 없으면 409 ROOM_NOT_READY, 팟 외 사용자는 404, 미확정·이탈 멤버는 403입니다. 대표는 본인의 PREPAY_RECEIVE 계좌 등록 이후에 채팅방 조회·이력 조회·전송이 가능하며, 미등록이면 409 ROOM_NOT_READY입니다. 대표 계좌 등록에 필요한 내부 roomId는 기존 API-23 확정 응답 또는 API-22 팟 조회에서 받습니다. 방 내부 생성과 대표의 채팅 입장 허용 시점은 구분합니다.

**응답 범위:** 기존 Room 필드를 유지합니다. 4자리 확인 코드는 매칭에서 생성한 기존 암호화 값을 읽어 선행 0을 유지한 문자열로 반환합니다. 거점·멤버 이름·학교·하차 동 등 팟 표시 정보와 나가기·택시 호출·탑승 버튼은 API-22~26을 사용하고 계좌·정산 카드는 해당 API를 별도로 조회합니다. 성공 응답은 Cache-Control: no-store입니다.

**방 수명:** 출발 +1시간과 대표의 정산 확정 시각(settlements.finalized_at) 중 늦은 시각에 종료합니다. 정산 생성 전 또는 미확정이면 종료를 연장하며, 실제 외부 송금 수령 확인까지 기다리는 규칙은 추가하지 않습니다. 팟 해산은 즉시 종료합니다. 종료 후 방·이력 조회는 410 ROOM_ACCESS_EXPIRED, 전송은 409 ROOM_CLOSED이며 완료한 전송의 재시도도 현재 접근 권한·수명을 검사합니다. 1분 주기 작업이 상태를 CLOSED로 기록하고 계좌를 삭제하며, 요청도 같은 시각 조건을 검사해 작업 주기 사이에 접근하지 못하게 합니다. 계좌 삭제 시 transfers.receiver_account_id만 NULL로 바꾸고 금액·송수신 확인 이력은 보존합니다. 정산 확정 기능과의 실제 연결 검증은 해당 담당 작업과 함께 확인해야 합니다.

**보존 구분:** 방 접근 종료와 채팅 기록의 물리 삭제는 다릅니다. 기획 질문 v3에 따라 API-30은 신고 시점까지 방 전체 대화의 증거 사본을 별도 보관하고 신고 접수부터 7일 뒤 사본·설명을 정리합니다. 이 정리는 이용 중인 채팅방의 메시지를 삭제하지 않습니다. 일반 대화의 물리 삭제 기준은 별도이며 Room.deleteAfter는 null입니다. 방 생성·개인정보 주의·집결 10분 전·멤버 이탈·택시 호출 안내는 서버 전용 SYSTEM 메시지로 중복 없이 기록합니다. 집결 안내는 출발 10분 전부터 출발 전까지 1분 주기로 생성하며, 시스템 메시지는 수정·삭제 API를 제공하지 않습니다.

**업무 오류:** `409 ROOM_NOT_READY`, `410 ROOM_ACCESS_EXPIRED`

**ERD 연결:** `chat_rooms`, `ride_parties`, `party_members`

### API-28 · GET /rooms/{roomId}/messages · 채팅 이력·새 메시지 조회

**권한:** 해당 방 접근권한을 가진 참여자 (`ROOM_MEMBER`) · **성공:** 200 · 응답 `data: Message[]`

경로 `roomId` 필수

| Query | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `afterId` | string | 아니오 | 이 ID 이후 신규 메시지; 형식: `^[1-9][0-9]*$` |
| `beforeId` | string | 아니오 | 이 ID 이전 과거 메시지; 형식: `^[1-9][0-9]*$` |
| `limit` | integer | 아니오 | 크기; 최소 1; 최대 100; 기본 50 |

**요청 본문:** 없음

**처리·검증:** API-27과 동일한 확정 멤버·대표 계좌·방 수명 조건으로 조회합니다. HTTP cursor 폴링을 구현하며 WebSocket/SSE는 도입하지 않습니다. afterId는 해당 ID보다 큰 신규 메시지를 ID 오름차순, beforeId는 해당 ID보다 작은 과거 메시지를 ID 내림차순으로 반환합니다. 두 값 동시 입력, 양수가 아닌 ID, limit 범위 위반은 422 VALIDATION_ERROR입니다. 초기 조회는 최신 페이지부터 ID 내림차순입니다. 마스킹된 저장 본문만 반환하고 응답은 Cache-Control: no-store입니다.

**페이지 정보:** 성공 본문은 data: Message[]와 meta: MessagePageMeta입니다. nextBeforeId는 과거 방향에 다음 페이지가 있을 때 마지막 반환 ID이며 신규 조회에서는 null입니다. latestId는 이번 응답 중 가장 큰 ID이며 빈 결과에서는 null입니다. hasMore는 요청 방향에 남은 페이지가 있는지 표시합니다. 신규 조회에서 hasMore=true이면 이번 latestId를 afterId로 다음 페이지를 이어 조회합니다. 빈 결과에서는 프런트가 기존 신규 조회 기준 ID를 유지합니다. 팟 잠금으로 메시지 쓰기·조회·이탈을 직렬화하여 미커밋 메시지로 인한 커서 누락을 방지합니다.

**업무 오류:** `401 UNAUTHENTICATED`, `403 FORBIDDEN`, `403 ACCOUNT_SUSPENDED`, `404 RESOURCE_NOT_FOUND`, `409 ROOM_NOT_READY`, `410 ROOM_ACCESS_EXPIRED`, `422 VALIDATION_ERROR` 및 공통 오류

**ERD 연결:** `chat_messages`, `chat_rooms`, `party_members`

### API-29 · POST /rooms/{roomId}/messages · 채팅 메시지 전송

**권한:** 해당 방 접근권한을 가진 참여자 (`ROOM_MEMBER`) · **성공:** 201 · 응답 `data: Message`

경로 `roomId` 필수 · `Idempotency-Key` 필수

**요청 본문:** `MessageCreate` · application/json

```json
{
  "clientMessageId": "0b18e222-668f-4b29-a013-6f1169e7a003",
  "text": "집결 장소에 도착했어요."
}
```

**처리·검증:** API-27과 동일한 확정 멤버·대표 계좌 조건을 적용하며 OPEN 방에서만 전송합니다. 로그인 쿠키·X-CSRF-Token·UUID Idempotency-Key가 필수입니다. clientMessageId도 UUID 문자열이고 text는 JSON 문자열만 허용합니다. v0.4 기준 공백만 있는 메시지와 200자를 초과한 입력은 422 VALIDATION_ERROR입니다(Java 문자열 길이 기준). 정의하지 않은 senderMemberId·messageType 등도 422입니다. senderMemberId는 세션의 팟 멤버에서 결정하고 SYSTEM은 서버만 생성합니다.

**마스킹:** 유니코드 NFKC 정규화와 포맷 문자 제거 후 전화번호·공백/하이픈/점/슬래시(`/`)/밑줄(`_`) 등 구분자 포함 8자리 이상 숫자·웹 링크를 '가려진 번호'로 치환하고 앞뒤 공백을 제거하여 저장·응답합니다. 예를 들어 `010/1234/5678`과 `1234_5678_9012`도 가립니다. 4자리 확인 코드는 그대로 보존합니다. 마스킹 후 표시할 내용이 없으면 422 MESSAGE_REJECTED입니다. 원문은 DB·로그·알림에 저장하지 않으며 응답은 Cache-Control: no-store입니다. 이 API 자체에서 별도 푸시 발송은 추가하지 않습니다.

**재시도·동시성:** 같은 사용자·방·clientMessageId·원문 text와 같은 키는 기존 메시지를 201로 반환합니다. 같은 clientMessageId의 동일 요청을 새 키로 재전송해도 같은 메시지입니다. 같은 키 또는 같은 clientMessageId의 다른 사용자·방·본문은 409 IDEMPOTENCY_CONFLICT입니다. 마스킹 결과가 같아도 서로 다른 원문은 충돌하며 원문 대신 VERIFICATION_SECRET 기반 HMAC으로 비교합니다. 키·client UUID·팟 순서의 잠금과 메시지/멱등 기록의 단일 트랜잭션으로 중복 생성과 부분 저장을 방지합니다. 현재 멤버·대표 계좌·방 수명을 재시도에도 확인하고 이탈·종료 뒤에는 과거 성공 결과를 재노출하지 않습니다.

**저장:** chat_messages에 request_hash와 서버 전용 system_event_key(UNIQUE), (room_id,id) 인덱스를 추가했습니다. chat_send_receipts는 키·요청 HMAC·메시지 ID만 저장하며 메시지 보존 기간 동안 유지합니다. 추후 메시지 물리 삭제를 구현할 때 이 기록도 함께 정리해야 합니다. 기존 VERIFICATION_SECRET(32바이트 이상)을 재사용하며 새 필수 환경변수는 없습니다.

**업무 오류:** `401 UNAUTHENTICATED`, `403 FORBIDDEN`, `403 ACCOUNT_SUSPENDED`, `404 RESOURCE_NOT_FOUND`, `409 ROOM_NOT_READY`, `409 ROOM_CLOSED`, `422 VALIDATION_ERROR`, `422 MESSAGE_REJECTED`, `409 IDEMPOTENCY_CONFLICT`, `503 POLICY_NOT_CONFIGURED` 및 공통 오류

**ERD 연결:** `chat_messages`, `chat_rooms`

### API-30 · POST /rooms/{roomId}/reports · 멤버·메시지 신고

**권한:** 해당 방 접근권한을 가진 참여자 (`ROOM_MEMBER`) · **성공:** 201 · 응답 `data: SafetyReport`

경로 `roomId`는 양의 bigint · 로그인 세션 쿠키, UUID `Idempotency-Key`, `X-CSRF-Token` 필수 · operationId: `reportMember`

**요청 본문:** `ReportCreate` · application/json

```json
{
  "targetMemberId": "502",
  "reasonCode": "PRIVACY_REQUEST",
  "messageId": "702",
  "details": "개인 연락처를 요구했어요."
}
```

**성공 응답 예시:**

```json
{
  "data": {
    "id": "801",
    "status": "RECEIVED",
    "evidenceDeleteAfter": "2026-11-12T23:21:00+09:00",
    "rematchingBlocked": true
  }
}
```

**접근·대상 검증:** 현재 채팅방에 입장 가능한 ACCEPTED 학생만 신고합니다. 팟 외 사용자는 404, 미확정·이탈 멤버는 403, 대표 계좌 미등록은 409 ROOM_NOT_READY, 종료된 방은 409 ROOM_CLOSED입니다. 본인을 제외한 같은 팟의 멤버만 대상이며 대상이 이미 이탈했어도 같은 팟 소속이면 신고할 수 있습니다. messageId는 선택 사항이며, 전달하면 같은 방에서 대상이 작성한 USER 메시지여야 합니다. 본인·없는 멤버·타 팟 멤버·타 방/다른 작성자/SYSTEM/없는 메시지는 422 INVALID_REPORT_TARGET입니다. 노쇼·확인 코드 불일치는 사용자가 고르는 사유이며 자동 판정하지 않습니다.

**입력·저장:** ID는 양의 bigint 범위 JSON 문자열, 사유는 정의된 5개 코드 중 하나입니다. details는 선택 JSON 문자열이며 v0.4 기준 최대 200자(Java 문자열 길이)입니다. OTHER에도 설명을 필수로 요구하지 않습니다. 알 수 없는 필드와 문자열 아닌 값은 422 VALIDATION_ERROR입니다. 신고 접수 시점까지의 해당 방 전체 대화(USER·SYSTEM)와 선택 설명을 기존 번호·링크·계좌 마스킹 후 암호화해 사본으로 저장합니다. 이후 대화는 이 사본에 추가하지 않습니다. 캡처 첨부·파일 업로드와 운영자 신고 검토 API는 이번 범위에 없습니다. 응답은 기존 SafetyReport 필드를 유지하고 Cache-Control: no-store를 적용하며 설명·증거 원문을 반환하지 않습니다.

**중복·재시도:** 기획 질문 v3 기준 같은 방에서 동일 신고자가 동일 대상을 한 번만 신고합니다. 새 키로 사유·메시지·설명을 바꿔 다시 신고해도 409 REPORT_ALREADY_EXISTS입니다. 다른 방의 신고와 상대방의 역방향 신고는 별개입니다. 동일 키·동일 사용자·방·대상·사유·messageId·설명 원문은 기존 신고 ID·만료 시각을 201로 반환하고 사본을 다시 만들거나 만료를 연장하지 않습니다. 같은 키에 다른 입력은 409 IDEMPOTENCY_CONFLICT이며, 마스킹 결과가 같아도 원문이 다르면 충돌합니다. 키는 UUID 대소문자를 정규화합니다. 재시도도 현재 접근 권한과 방 수명을 검사합니다.

**재매칭 차단:** 신고·증거·멱등 기록·정규화한 사용자 쌍 차단을 하나의 트랜잭션으로 저장합니다. 사용자 ID의 작은 값/큰 값 순서로 user_blocks에 기록하고 기존 관계는 유지합니다. 기존 매칭과 슬롯 대기 인원 조회의 차단 필터를 재사용합니다. 신고 후 현재 팟을 해산하거나 대화를 차단하지 않으며, 신고 횟수에 따른 자동 제외·노쇼 기록·이용 정지는 적용하지 않습니다.

**보존·정리:** 각 신고의 접수 시각부터 정확히 7일을 evidenceDeleteAfter로 반환합니다. 다른 신고와 별도로 계산하고 연장하지 않습니다. 1분 주기 정리 작업이 만료 시각 이하인 신고의 암호화 사본(설명 포함)과 messageId 참조를 NULL로 지웁니다. 원래 채팅 메시지와 다른 미만료 신고 증거는 유지합니다. 신고 ID·방·당사자·사유·상태 등 접수 메타데이터와 멱등 HMAC은 중복 방지·재시도를 위해 유지하며, 사본 삭제 후에도 재매칭 차단을 유지합니다. 증거를 조회하는 사용자 API는 제공하지 않습니다.

**DB·설정:** safety_reports의 (room_id,reporter_member_id,target_member_id) UNIQUE와 evidence_delete_after 인덱스, safety_report_receipts(request_key PK, request_hash HMAC, report_id)가 추가됩니다. 기존 암호화 키와 VERIFICATION_SECRET(32바이트 이상)을 재사용하며 새 필수 환경변수는 없습니다.

**업무 오류:** `401 UNAUTHENTICATED`, `403 FORBIDDEN`, `403 ACCOUNT_SUSPENDED`, `404 RESOURCE_NOT_FOUND`, `409 ROOM_NOT_READY`, `409 ROOM_CLOSED`, `409 REPORT_ALREADY_EXISTS`, `409 IDEMPOTENCY_CONFLICT`, `422 VALIDATION_ERROR`, `422 INVALID_REPORT_TARGET`, `503 POLICY_NOT_CONFIGURED` 및 공통 오류

**ERD 연결:** `safety_reports`, `user_blocks`, `chat_messages`, `party_members`

### 계좌·정산

### API-31 · PUT /rooms/{roomId}/accounts/{purpose} · 내 선송금·환급 수취 계좌 등록

**권한:** 본인 계좌의 팟 참여자 (`ACCOUNT_OWNER`) · **성공:** 200 · 응답 `data: MaskedAccount`

경로 `roomId` 필수 · 경로 `purpose` 필수 · `Idempotency-Key` 필수

**요청 본문:** `AccountInput` · application/json

```json
{
  "bankCode": "DEMO",
  "accountNumber": "000000000000",
  "holderName": "박대표"
}
```

**처리·검증:** 소유자는 세션의 해당 팟 member로 결정. PREPAY_RECEIVE는 대표, REFUND_RECEIVE는 일반 멤버 본인만 등록. 만료된 방은 등록 불가. 미송금 PENDING 이체에 수취 계좌를 연결. 해당 계좌로 SENT/RECEIVED된 이체가 있으면 덮어쓰기 금지. 영구 재사용 저장은 제공하지 않음.

**업무 오류:** `403 ACCOUNT_PURPOSE_NOT_ALLOWED`, `409 ACCOUNT_LOCKED`, `409 ROOM_CLOSED`

**ERD 연결:** `room_accounts`, `transfers`, `chat_rooms`, `party_members`

### API-32 · GET /transfers/{transferId}/recipient-account · 송금할 수취 계좌 조회

**권한:** 해당 이체의 송금자 (`TRANSFER_PAYER`) · **성공:** 200 · 응답 `data: RecipientAccount`

경로 `transferId` 필수

**요청 본문:** 없음

**처리·검증:** 해당 이체의 송금자에게만 원문 계좌 제공. PREPAY는 멤버→대표, REFUND는 대표→해당 멤버. 계좌 소유자가 payee인지 확인하고 방 종료/계좌 삭제 후에는 410. Cache-Control: no-store.

**업무 오류:** `409 ACCOUNT_NOT_REGISTERED`, `410 ACCOUNT_EXPIRED`

**ERD 연결:** `transfers`, `room_accounts`, `party_members`

### API-33 · GET /parties/{partyId}/settlement · 예상·선송금·최종 정산 조회

**권한:** 해당 팟 참여자 (`PARTY_MEMBER`) · **성공:** 200 · 응답 `data: Settlement`

경로 `partyId` 필수 · 성공 응답 `ETag`

**요청 본문:** 없음

**처리·검증:** 대표·일반 멤버 모두에게 팟 전원의 개인 정산 항목을 반환(정산내역-멤버 화면이 전원 분담·송금 현황을 보여줌). 타인 환급 계좌번호와 정확한 경로는 포함하지 않음(계좌번호는 응답 DTO에 없고 API-31로 인가된 송금자에게만). 생성 전에는 409 SETTLEMENT_NOT_READY.

**업무 오류:** `409 SETTLEMENT_NOT_READY`

**ERD 연결:** `settlements`, `settlement_items`, `transfers`, `party_members`

### API-34 · POST /parties/{partyId}/settlement/receipt · 대표 결제 화면 첨부

**권한:** 해당 팟 대표 (`REPRESENTATIVE`) · **성공:** 201 · 응답 `data: Receipt`

경로 `partyId` 필수 · `Idempotency-Key` 필수 · `If-Match` 필수 · 성공 응답 `ETag`

**요청 본문:** `ReceiptUpload` · multipart/form-data (`file` 바이너리 1개)

**처리·검증:** multipart/form-data file 1개. 제안 제한 JPEG/PNG/WebP, 5MiB 이하; 실제 MIME·디코딩 검증, 메타데이터 제거, 비공개 객체 저장. 정산 확정 전만 교체 가능. 다른 팟 파일 참조 불가. 영수증은 OCR/결제 사실 자동 검증이 아님.

**업무 오류:** `415 INVALID_FILE_TYPE`, `413 FILE_TOO_LARGE`, `409 SETTLEMENT_FINALIZED`

**ERD 연결:** `settlements`

### API-35 · GET /parties/{partyId}/settlement/receipt · 참여자 결제 화면 열기

**권한:** 해당 팟 참여자 (`PARTY_MEMBER`) · **성공:** 200 · 응답 `data: Download`

경로 `partyId` 필수

**요청 본문:** 없음

**처리·검증:** 해당 팟 참여자에게 만료되는 비공개 URL 발급. 영수증 보관 기간이 지나면 410. 계좌나 신용카드 민감 항목 노출을 최소화하도록 업로드 안내/처리 필요.

**업무 오류:** `404 RECEIPT_NOT_FOUND`, `410 RECEIPT_EXPIRED`

**ERD 연결:** `settlements`, `party_members`

### API-36 · PATCH /parties/{partyId}/settlement/route · 정산 전 실제 하차 순서 수정

**권한:** 해당 팟 대표 (`REPRESENTATIVE`) · **성공:** 200 · 응답 `data: Settlement`

경로 `partyId` 필수 · `Idempotency-Key` 필수 · `If-Match` 필수 · 성공 응답 `ETag`

**요청 본문:** `RoutePatch` · application/json

```json
{
  "orderedMemberIds": [
    "501",
    "502",
    "503"
  ],
  "expectedRouteVersion": 1
}
```

**처리·검증:** 확정 전 현재 계산 대상 멤버 ID를 빠짐없이 한 번씩 전달. 서버가 저장된 핀으로 도로 구간거리를 다시 계산하고 route_version 증가. 클라이언트 거리·분담액은 받지 않음. 이미 받은 선송금액은 재계산으로 바꾸지 않음. 마지막 하차자 변경/미탑승자 제거는 정책 확인 전 차단.

**업무 오류:** `409 ROUTE_VERSION_CONFLICT`, `422 INVALID_STOP_ORDER`, `409 REPRESENTATIVE_CHANGE_REQUIRES_POLICY`, `409 SETTLEMENT_FINALIZED`, `503 UPSTREAM_UNAVAILABLE`

**ERD 연결:** `party_stops`, `ride_parties`, `settlements`, `settlement_items`

### API-37 · POST /parties/{partyId}/settlement/preview · 실제 요금 분담·환급 미리보기

**권한:** 해당 팟 대표 (`REPRESENTATIVE`) · **성공:** 200 · 응답 `data: FarePreview`

경로 `partyId` 필수

**요청 본문:** `FareInput` · application/json

```json
{
  "actualTotalWon": 30000,
  "routeVersion": 2
}
```

**처리·검증:** 저장하지 않는 계산. 원본 상한 actualTotalWon <= floor(estimatedTotalWon*1.2). 예정 경로 구간거리/구간 탑승 인원으로 나누고 원 단위 나머지 배분. 차액 = 선송금 − 확정 분담(양수 환급 대표→멤버, 음수 추가 납부 멤버→대표). 대표 환급 0. 음수 차액(추가 납부)은 디자인(S-06w)에 따라 허용하여 확정을 막지 않는다(canFinalize=true). blockingReasons 는 미탑승 등 후속 정책용으로 지금은 빈 목록.

**업무 오류:** `422 FARE_LIMIT_EXCEEDED`, `409 ROUTE_VERSION_CONFLICT`, `409 INVALID_ROUTE_DISTANCE`, `409 SETTLEMENT_FINALIZED`

**ERD 연결:** `settlements`, `settlement_items`, `party_stops`, `transfers`

### API-38 · POST /parties/{partyId}/settlement/finalize · 실제 요금·개인 부담·환급 확정

**권한:** 해당 팟 대표 (`REPRESENTATIVE`) · **성공:** 200 · 응답 `data: Settlement`

경로 `partyId` 필수 · `Idempotency-Key` 필수 · `If-Match` 필수 · 성공 응답 `ETag`

**요청 본문:** `FareInput` · application/json

```json
{
  "actualTotalWon": 30000,
  "routeVersion": 2
}
```

**처리·검증:** 미리보기 결과를 신뢰하지 않고 재계산. 선송금 전원 수령(RECEIVED) 전이면 409 PREPAY_NOT_COMPLETED. 개인 부담 합계=실제 요금, finalized_at·actual_total_won 기록. 차액 = 선송금 − 확정 분담: 양수 환급은 PENDING REFUND 이체(대표→멤버) 생성, 음수 차액은 추가 납부로 허용하여 이체 없이 항목에 음수로 기록(멤버가 수동 송금, 디자인 S-06w), 대표/0원은 이체 없이 완료. 차액이 남으면 정산 REFUNDING·항목 REFUND_PENDING, 없으면 COMPLETED. 확정 직전 조회 ETag 를 If-Match 로 확인(불일치 412). 이미 확정됐으면 같은 응답(멱등).

**업무 오류:** `422 FARE_LIMIT_EXCEEDED`, `409 ROUTE_VERSION_CONFLICT`, `409 PREPAY_NOT_COMPLETED`, `409 ADDITIONAL_PAYMENT_POLICY_REQUIRED`, `409 BOARDING_POLICY_REQUIRED`, `409 SETTLEMENT_FINALIZED`

**ERD 연결:** `settlements`, `settlement_items`, `transfers`, `notifications`, `party_stops`

### API-39 · POST /transfers/{transferId}/sent · 송금자의 ‘보냈어요’

**권한:** 해당 이체의 송금자 (`TRANSFER_PAYER`) · **성공:** 200 · 응답 `data: Transfer`

경로 `transferId` 필수 · `Idempotency-Key` 필수

**요청 본문:** 없음

**처리·검증:** 서버 계산 금액과 수취 계좌가 확정된 PENDING 이체만 SENT로 변경. 타인 이체·금액·수취인 변경 불가. SENT/RECEIVED 반복 요청은 현재 상태 반환. 실제 은행 이체를 실행하지 않음. PREPAY 신규 송금 확인은 양측의 팟 참여 확정 후, REFUND 송금 확인은 정산 확정 후만 허용.

**업무 오류:** `409 ACCOUNT_NOT_REGISTERED`, `410 ACCOUNT_EXPIRED`, `409 TRANSFER_CANCELLED`

**ERD 연결:** `transfers`, `room_accounts`

### API-40 · POST /transfers/{transferId}/received · 수취자의 ‘받았어요’

**권한:** 해당 이체의 수취자 (`TRANSFER_PAYEE`) · **성공:** 200 · 응답 `data: Transfer`

경로 `transferId` 필수 · `Idempotency-Key` 필수

**요청 본문:** 없음

**처리·검증:** 수취자만 은행 앱 확인 후 RECEIVED로 기록. PENDING에서도 실제 수령 확인이 가능하도록 허용하는 설계 제안. 반복 호출은 상태 유지. 전원 선송금 확인 시 호출 가능, 전원 환급 확인 시 정산 완료. 계좌 삭제 후에도 이미 보낸 돈의 수령 확인은 이체 기록 권한으로 가능. 선송금/환급 단계와 해당 이체의 수취 권한을 확인하되, 이미 발송된 돈의 수령 확인을 단순 계정 정지나 방 종료로 막지 않는 접근 예외는 정책 확정 대상.

**확인할 제안:** 송금자 확인보다 수취 확인이 먼저 와도 허용하는 순서 정책 제안

**업무 오류:** `409 TRANSFER_CANCELLED`

**ERD 연결:** `transfers`, `settlement_items`, `settlements`, `ride_parties`

### 알림

### API-41 · GET /me/notifications · 내 알림 조회

**권한:** 로그인 사용자 (`AUTHENTICATED`) · **성공:** 200 · 응답 `data: Notification[]`

| Query | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `cursor` | string | 아니오 | 다음 페이지 커서 |
| `limit` | integer | 아니오 | 페이지 크기; 최소 1; 최대 100; 기본 20 |

**요청 본문:** 없음

**처리·검증:** 본인 알림만. 제목/내용/이동 경로는 허용 목록으로 구성하며 계좌·정확한 핀·실명 제외.

**ERD 연결:** `notifications`

### API-42 · POST /me/notifications/{notificationId}/read · 알림 읽음 처리

**권한:** 로그인 사용자 (`AUTHENTICATED`) · **성공:** 200 · 응답 `data: Notification`

경로 `notificationId` 필수 · `Idempotency-Key` 필수

**요청 본문:** 없음

**처리·검증:** 본인 알림의 read_at을 최초 시각으로 설정. 반복 호출은 기존 시각 유지.

**ERD 연결:** `notifications`

### 수요 지도

### API-43 · GET /demand/candidates · 수요 지도·노선 후보 목록

**권한:** 로그인 사용자 (`AUTHENTICATED`) · **성공:** 200 · 응답 `data: DemandCandidate[]`

| Query | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `runId` | string | 아니오 | 생략하면 최신 성공 실행; 응답 meta.runId를 이후 고정; 형식: `^[1-9][0-9]*$` |
| `periodStart` | string (date) | 아니오 | 집계 시작일. periodEnd와 함께 입력; runId가 있으면 해당 기간과 일치해야 함 |
| `periodEnd` | string (date) | 아니오 | 집계 종료일. 미산출 기간이면 409 DEMAND_NOT_READY |
| `weekday` | integer | 아니오 | 현지 요일; 최소 0; 최대 6 |
| `slotStart` | string | 아니오 | 30분 슬롯 HH:mm; 형식: `^(?:[01][0-9]|2[0-3]):(?:00|30)$` |
| `candidateOnly` | boolean | 아니오 | 후보만 조회; 기본 false |
| `cursor` | string | 아니오 | 다음 페이지 커서 |
| `limit` | integer | 아니오 | 페이지 크기; 최소 1; 최대 100; 기본 20 |

**요청 본문:** 없음

**처리·검증:** 학생/관계자 모두 최소 표본 기준을 통과한 셀만 반환. 동일 runId로 페이지와 필터를 고정. 기본 정렬은 원본의 추정 수요 내림차순, 동률 ID 오름차순. 초기 권한 제안은 자기 대학 범위. v0.2 복합 점수는 사용하지 않음. 기간 필터는 이미 성공적으로 산출된 run과 일치해야 한다. 원하는 기간의 산출이 없으면 최신 데이터로 몰래 대체하지 않는다. 요약 지표도 표시 가능한 셀만 합산한다.

**업무 오류:** `409 DEMAND_NOT_READY`, `403 SCOPE_NOT_ALLOWED`, `503 POLICY_NOT_CONFIGURED`

**ERD 연결:** `demand_runs`, `demand_estimates`, `universities`, `pickup_points`, `administrative_areas`

### API-44 · GET /demand/candidates/{estimateId} · 노선 구간·근거 지표 상세

**권한:** 로그인 사용자 (`AUTHENTICATED`) · **성공:** 200 · 응답 `data: DemandCandidate`

경로 `estimateId` 필수

**요청 본문:** 없음

**처리·검증:** 현재 권한·최소 표본 조건을 다시 확인. 숨김/다른 학교/없는 ID는 동일 404. 관측수와 추정치를 분리하고 출처·시점·단위·해상도 명시.

**업무 오류:** `404 RESOURCE_NOT_FOUND`

**ERD 연결:** `demand_estimates`, `demand_runs`

### API-45 · GET /me/demand-contribution · 내 귀가 반영 횟수·후보 여부

**권한:** 학생 (`STUDENT`) · **성공:** 200 · 응답 `data: Contribution`

| Query | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `runId` | string | 예 | 산출 버전; 형식: `^[1-9][0-9]*$` |
| `pickupPointId` | string | 예 | 출발 거점; 형식: `^[1-9][0-9]*$` |
| `destinationDongId` | string | 예 | 도착 동; 형식: `^[1-9][0-9]*$` |

**요청 본문:** 없음

**처리·검증:** 본인의 선택 구간 귀가만 중복 제거 집계. 공개 표본 미달이면 후보 순위·다른 사람 수요·남은 수요 역산값을 노출하지 않고 candidateId/remainingToCandidate=NULL.

**업무 오류:** `409 DEMAND_NOT_READY`

**ERD 연결:** `journeys`, `demand_estimates`, `demand_runs`

### 관계자 리포트

### API-46 · POST /staff/reports · 후보·조치로 리포트 초안 생성

**권한:** 학교 관계자 (`STAFF`) · **성공:** 201 · 응답 `data: StakeholderReport`

`Idempotency-Key` 필수

**요청 본문:** `StakeholderReportCreate` · application/json

```json
{
  "runId": "1401",
  "format": "PDF",
  "filters": {
    "periodStart": "2026-10-29",
    "periodEnd": "2026-11-04",
    "weekday": 4,
    "slotStart": "23:30"
  },
  "items": [
    {
      "estimateId": "1501",
      "action": "CITY_PROPOSAL"
    }
  ]
}
```

**처리·검증:** created_by는 로그인 STAFF. 동일 성공 run의 공개 후보만 포함; 중복 후보 금지. 선택 필터와 산출 기간을 검증하고 서버가 비식별 candidate_snapshot 구성. 요청에서 임의 수요·순위·이미지/파일 경로를 받지 않음.

**업무 오류:** `422 MIXED_DEMAND_RUN`, `422 SUPPRESSED_CANDIDATE`, `422 REPORT_FILTER_MISMATCH`, `422 REPORT_LAYOUT_LIMIT`

**ERD 연결:** `stakeholder_reports`, `stakeholder_report_items`, `demand_estimates`, `demand_runs`

### API-47 · GET /staff/reports · 내 리포트 목록

**권한:** 학교 관계자 (`STAFF`) · **성공:** 200 · 응답 `data: StakeholderReport[]`

| Query | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `cursor` | string | 아니오 | 다음 페이지 커서 |
| `limit` | integer | 아니오 | 페이지 크기; 최소 1; 최대 100; 기본 20 |

**요청 본문:** 없음

**처리·검증:** 초기 권한 제안은 본인이 생성한 리포트만. 팀 공동열람은 정책 확정 후 확장.

**ERD 연결:** `stakeholder_reports`, `stakeholder_report_items`

### API-48 · GET /staff/reports/{reportId} · 리포트 미리보기·생성 상태 조회

**권한:** 리포트를 만든 관계자 (`REPORT_OWNER`) · **성공:** 200 · 응답 `data: StakeholderReport`

경로 `reportId` 필수 · 성공 응답 `ETag`

**요청 본문:** 없음

**처리·검증:** 스냅샷 기반 1페이지 내용을 조회. 파일 생성 중에는 polling으로 GENERATING→READY/FAILED 확인. 다른 생성자 리포트는 404.

**ERD 연결:** `stakeholder_reports`, `stakeholder_report_items`

### API-49 · POST /staff/reports/{reportId}/exports · 리포트 PDF·이미지 생성

**권한:** 리포트를 만든 관계자 (`REPORT_OWNER`) · **성공:** 202 · 응답 `data: StakeholderReport`

경로 `reportId` 필수 · `Idempotency-Key` 필수 · `If-Match` 필수 · 성공 응답 `ETag`

**요청 본문:** 없음

**처리·검증:** DRAFT/FAILED→GENERATING 작업 enqueue. READY/GENERATING 반복은 기존 결과를 반환하고 중복 작업 없음. 출력 형식은 리포트 생성 시 정한 값. 새 형식/후보/조치는 새 초안 생성. 개인정보·표본 숨김 조건을 출력 전에 재검증.

**업무 오류:** `422 REPORT_LAYOUT_LIMIT`, `409 REPORT_CONTENT_UNAVAILABLE`

**ERD 연결:** `stakeholder_reports`, `stakeholder_report_items`

### API-50 · GET /staff/reports/{reportId}/download · 완성된 리포트 다운로드 URL

**권한:** 리포트를 만든 관계자 (`REPORT_OWNER`) · **성공:** 200 · 응답 `data: Download`

경로 `reportId` 필수

**요청 본문:** 없음

**처리·검증:** READY 파일의 만료되는 비공개 URL 발급. 파일 유형 PDF 또는 image/png. 제출/외부 발송은 수행하지 않음.

**업무 오류:** `409 REPORT_NOT_READY`, `410 REPORT_FILE_EXPIRED`

**ERD 연결:** `stakeholder_reports`

## 5. 요청·응답 DTO 필드

단건 응답은 아래 모델을 `data`에 넣습니다. 목록은 해당 모델의 배열입니다. `A / null`은 명시적 NULL을 허용합니다. 필수 ‘예’는 키 자체가 필수라는 뜻입니다. `enum`·길이·범위는 OpenAPI 파일에도 동일하게 들어 있습니다. 중첩 객체는 표에 적힌 모델명을 참조합니다.

### Point

| 필드 | 타입 | 필수 | 의미·제약 |
| --- | --- | --- | --- |
| `latitude` | number | 예 | 위도; 최소 -90; 최대 90 |
| `longitude` | number | 예 | 경도; 최소 -180; 최대 180 |

### University

| 필드 | 타입 | 필수 | 의미·제약 |
| --- | --- | --- | --- |
| `id` | string | 예 | 대학 ID; 형식: `^[1-9][0-9]*$` |
| `name` | string | 예 | 대학명 |
| `emailDomains` | string[] | 예 | 가입 가능한 도메인 |

### ProfileOption

| 필드 | 타입 | 필수 | 의미·제약 |
| --- | --- | --- | --- |
| `code` | string | 예 | 기획에서 승인한 값 |
| `label` | string | 예 | 화면 표시명 |

### ProfileOptions

| 필드 | 타입 | 필수 | 의미·제약 |
| --- | --- | --- | --- |
| `genderOptions` | ProfileOption[] | 예 | FEMALE/여성, MALE/남성 순서 |
| `sameGenderDefault` | boolean | 예 | 초기 기본값 true. 가입 시 켜기·끄기 모두 허용 |

### Bank

| 필드 | 타입 | 필수 | 의미·제약 |
| --- | --- | --- | --- |
| `code` | string | 예 | 은행 코드 |
| `name` | string | 예 | 은행명 |

### PickupPoint

| 필드 | 타입 | 필수 | 의미·제약 |
| --- | --- | --- | --- |
| `id` | string | 예 | 고정 거점 ID; 형식: `^[1-9][0-9]*$` |
| `universityId` | string / null | 예 | 공용이면 NULL |
| `universityName` | string / null | 예 | 학교 이름; 공용이면 NULL |
| `name` | string | 예 | 거점명 |
| `point` | Point | 예 | 해당 DTO 참조 |
| `meetingNote` | string | 예 | 공개 집결 안내 |

### User

| 필드 | 타입 | 필수 | 의미·제약 |
| --- | --- | --- | --- |
| `id` | string | 예 | 계정 ID; 형식: `^[1-9][0-9]*$` |
| `universityId` | string | 예 | 소속 대학 ID; 형식: `^[1-9][0-9]*$` |
| `universityName` | string | 예 | 소속 대학명. 가입·현재 세션 조회 응답에 포함 |
| `email` | string (email) | 예 | 본인 이메일 |
| `role` | string | 예 | 역할; STUDENT / STAFF |
| `status` | string | 예 | 계정 상태; ACTIVE / SUSPENDED / WITHDRAWN |

### Session

| 필드 | 타입 | 필수 | 의미·제약 |
| --- | --- | --- | --- |
| `user` | User | 예 | 해당 DTO 참조 |
| `expiresAt` | string (date-time) | 예 | 서버가 정한 세션 만료 |
| `csrfToken` | string | 예 | 쿠키 인증 변경 요청용 CSRF 토큰 |

### StudentProfile

| 필드 | 타입 | 필수 | 의미·제약 |
| --- | --- | --- | --- |
| `user` | User | 예 | 해당 DTO 참조 |
| `realName` | string | 예 | 본인에게만 실명 반환 |
| `genderCode` | string | 예 | 가입 시 확정한 성별 코드 |
| `sameGenderDefault` | boolean | 예 | 동성 기본 설정 |
| `defaultDongId` | string | 예 | 기본 행정동; 형식: `^[1-9][0-9]*$` |
| `defaultDongName` | string | 예 | 기본 하차 행정동 이름 |
| `defaultDropoff` | Point | 예 | 해당 DTO 참조 |

### ProfileInput

| 필드 | 타입 | 필수 | 의미·제약 |
| --- | --- | --- | --- |
| `realName` | string | 예 | 실명; 앞뒤 공백 제거 후 2~20자 |
| `genderCode` | string | 예 | ProfileOptions의 승인된 코드; 최소 1자; 최대 32자 |
| `sameGenderDefault` | boolean | 아니오 | true/false 선택값 저장. 생략/null이면 true |
| `defaultDropoff` | Point | 예 | 해당 DTO 참조 |

### ProfilePatch

| 필드 | 타입 | 필수 | 의미·제약 |
| --- | --- | --- | --- |
| `sameGenderDefault` | boolean | 아니오 | 이후 신청 기본값. true/false 모두 허용, 명시적 null은 거부 |
| `defaultDropoff` | Point | 아니오 | 기본 하차 좌표. 위도·경도 모두 숫자로 전달, 명시적 null은 거부 |

최소 1개 필드를 포함해야 합니다. 생략한 필드는 유지합니다. 이름·학교·이메일·성별 변경은 허용하지 않습니다(S-08 기준).

### EmailVerificationRequest

| 필드 | 타입 | 필수 | 의미·제약 |
| --- | --- | --- | --- |
| `universityId` | string | 예 | 선택 대학; 형식: `^[1-9][0-9]*$` |
| `email` | string (email) | 예 | 해당 대학 허용 이메일; 현재 구현은 최대 255자 |

### EmailVerification

| 필드 | 타입 | 필수 | 의미·제약 |
| --- | --- | --- | --- |
| `verificationId` | string | 예 | 발급 식별자; 형식: `^[1-9][0-9]*$` |
| `expiresAt` | string (date-time) | 예 | 발급 +10분 |
| `resendAvailableAt` | string (date-time) / null | 예 | 재발송 제한이 설정되면 안내 |

### CodeVerificationRequest

| 필드 | 타입 | 필수 | 의미·제약 |
| --- | --- | --- | --- |
| `code` | string | 예 | 6자리 인증번호; 형식: `^[0-9]{6}$` |

### VerificationGrant

| 필드 | 타입 | 필수 | 의미·제약 |
| --- | --- | --- | --- |
| `verificationToken` | string | 예 | 인증된 이메일·대학에 묶인 1회용 불투명 증표 |
| `expiresAt` | string (date-time) | 예 | 증표 만료; 설정값 |

### StudentRegistration

| 필드 | 타입 | 필수 | 의미·제약 |
| --- | --- | --- | --- |
| `verificationToken` | string | 예 | 검증 완료 증표 |
| `profile` | ProfileInput | 예 | 해당 DTO 참조 |

### StudentLogin

| 필드 | 타입 | 필수 | 의미·제약 |
| --- | --- | --- | --- |
| `verificationToken` | string | 예 | 학교 이메일 OTP 재인증으로 발급한 증표. 공백 불가, 최대 512자 |

### StaffLogin

| 필드 | 타입 | 필수 | 의미·제약 |
| --- | --- | --- | --- |
| `email` | string (email) | 예 | 발급된 관계자 이메일. 앞뒤 공백 제거·소문자, 최대 255자, JSON 문자열만 허용 |
| `password` | string (password) | 예 | 비어 있지 않은 UTF-8 72바이트 이하. 공백 보존, JSON 문자열만 허용 |

### TravelOptionsRequest

| 필드 | 타입 | 필수 | 의미·제약 |
| --- | --- | --- | --- |
| `pickupPointId` | string | 예 | 선택 거점; 형식: `^[1-9][0-9]*$` |
| `dropoff` | Point | 예 | 해당 DTO 참조 |
| `departureAt` | string (date-time) | 예 | 비교 기준 시각 |
| `currentLocation` | Point | 아니오 | 해당 DTO 참조 |

### TransitLeg

| 필드 | 타입 | 필수 | 의미·제약 |
| --- | --- | --- | --- |
| `mode` | string | 예 | 이동 수단; WALK / BUS / SUBWAY |
| `routeName` | string / null | 예 | 노선명 |
| `fromName` | string | 예 | 출발 지점 |
| `toName` | string | 예 | 도착 지점 |
| `departureAt` | string (date-time) | 예 | 구간 출발 |
| `arrivalAt` | string (date-time) | 예 | 구간 도착 |

### TransitOption

| 필드 | 타입 | 필수 | 의미·제약 |
| --- | --- | --- | --- |
| `available` | boolean | 예 | 안내 가능한 막차 존재 여부 |
| `lastLeaveAt` | string (date-time) / null | 예 | 현재 위치 기준 출발 마감; 위치 없으면 거점 기준 |
| `arrivalAt` | string (date-time) / null | 예 | 예상 도착 |
| `estimatedCostWon` | integer / null | 예 | 예상 교통비 |
| `legs` | TransitLeg[] | 예 | 제공 가능한 환승 경로 |
| `sourceLabel` | string | 예 | 출처와 관측·추정 구분 |
| `asOf` | string (date-time) | 예 | 자료 확인 시각 |

### TravelOptions

| 필드 | 타입 | 필수 | 의미·제약 |
| --- | --- | --- | --- |
| `transit` | TransitOption | 예 | 해당 DTO 참조 |
| `soloTaxiEstimatedWon` | integer / null | 예 | 혼자 택시 추정 비용 |
| `poolEstimatedShareWon` | integer / null | 예 | 아직 팟이 없으면 조건별 추정 |
| `estimatedArrivalAt` | string (date-time) / null | 예 | 택시 예상 도착 |
| `destinationDongId` | string | 예 | 핀으로 판별한 동; 형식: `^[1-9][0-9]*$` |
| `districtId` | string | 예 | 구 또는 구가 없는 시; 형식: `^[1-9][0-9]*$` |
| `asOf` | string (date-time) | 예 | 추정 기준 시각 |
| `warnings` | string[] | 예 | 불확실성 안내 |

### JourneyCreate

| 필드 | 타입 | 필수 | 의미·제약 |
| --- | --- | --- | --- |
| `clientJourneyKey` | string (uuid) | 예 | 생성 멱등키 |
| `pickupPointId` | string | 예 | 최종 출발 거점; 형식: `^[1-9][0-9]*$` |
| `dropoff` | Point | 예 | 해당 DTO 참조 |
| `intendedDepartureAt` | string (date-time) | 예 | 귀가 예정 시각 |

### Journey

| 필드 | 타입 | 필수 | 의미·제약 |
| --- | --- | --- | --- |
| `id` | string | 예 | 귀가 ID; 형식: `^[1-9][0-9]*$` |
| `universityId` | string | 예 | 귀가 당시 학교; 형식: `^[1-9][0-9]*$` |
| `universityName` | string | 예 | 학교 이름 |
| `pickupPointId` | string | 예 | 출발 거점; 형식: `^[1-9][0-9]*$` |
| `pickupPointName` | string | 예 | 출발 거점 이름 |
| `destinationDongId` | string | 예 | 하차 동; 형식: `^[1-9][0-9]*$` |
| `destinationDongName` | string | 예 | 하차 동 이름 |
| `intendedDepartureAt` | string (date-time) | 예 | 귀가 예정 시각 |
| `outcome` | string | 예 | 현재 결과; UNRESOLVED / POOL_BOARDED / BUS_SELECTED / SOLO_SELECTED / CANCELLED |
| `outcomeAt` | string (date-time) / null | 예 | 결과 시각 |
| `outcomeSource` | string / null | 예 | 결과 근거 |
| `activeRequestId` | string / null | 예 | 현재 신청 ID |

### JourneyChoice

| 필드 | 타입 | 필수 | 의미·제약 |
| --- | --- | --- | --- |
| `mode` | string | 예 | 선택한 귀가 수단; 탑승 성공을 직접 입력할 수 없음; BUS / SOLO |

### RideRequestCreate

| 필드 | 타입 | 필수 | 의미·제약 |
| --- | --- | --- | --- |
| `clientRequestKey` | string (uuid) | 예 | 신청 생성 멱등키 |
| `pickupPointId` | string | 예 | 이번 시도 거점; 형식: `^[1-9][0-9]*$` |
| `dropoff` | Point | 예 | 해당 DTO 참조 |
| `departureAt` | string (date-time) | 예 | 날짜를 포함한 30분 슬롯 |
| `sameGenderOnly` | boolean | 예 | 이번 신청 조건 |

### RideRequest

| 필드 | 타입 | 필수 | 의미·제약 |
| --- | --- | --- | --- |
| `id` | string | 예 | 신청 ID; 형식: `^[1-9][0-9]*$` |
| `journeyId` | string | 예 | 귀가 ID; 형식: `^[1-9][0-9]*$` |
| `pickupPointId` | string | 예 | 거점; 형식: `^[1-9][0-9]*$` |
| `pickupPointName` | string | 예 | 거점 이름 |
| `districtId` | string | 예 | 구 또는 시; 형식: `^[1-9][0-9]*$` |
| `districtName` | string | 예 | 구 또는 시 이름 |
| `destinationDongId` | string | 예 | 동; 형식: `^[1-9][0-9]*$` |
| `destinationDongName` | string | 예 | 동 이름 |
| `departureAt` | string (date-time) | 예 | 출발 슬롯 |
| `sameGenderOnly` | boolean | 예 | 동성 조건 |
| `status` | string | 예 | 신청 상태; WAITING / MATCHED / FAILED / CANCELLED / EXPIRED |
| `resultDueAt` | string (date-time) / null | 예 | 결과 안내 예정 마감 |
| `partyId` | string / null | 예 | 배정 팟 |
| `createdAt` | string (date-time) | 예 | 선착순 기준 시각 |

### MatchingSlot

| 필드 | 타입 | 필수 | 의미·제약 |
| --- | --- | --- | --- |
| `departureAt` | string (date-time) | 예 | 서버가 허용한 슬롯 |
| `waitingCount` | integer | 예 | 본인 조건과 호환되는 대기 인원; 본인 제외; 최소 0 |
| `registrationAllowed` | boolean | 예 | 신청 가능 |
| `reason` | string / null | 예 | 불가 사유 코드 |

### PartyMember

| 필드 | 타입 | 필수 | 의미·제약 |
| --- | --- | --- | --- |
| `memberId` | string | 예 | 팟 참여 ID; user ID를 노출하지 않음; 형식: `^[1-9][0-9]*$` |
| `maskedName` | string | 예 | 예: 김*연 |
| `universityName` | string | 예 | 학교 |
| `destinationDongName` | string | 예 | 하차 동만 공개 |
| `sameGenderAsMe` | boolean | 예 | 본인과 동일 성별 여부 |
| `gender` | string | 예 | 성별; FEMALE / MALE. 동성 매칭을 끈 사람도 있어 심야에 누구와 타는지 미리 알 수 있게 보여 주는 안전 정보(기획 답변 v2 #12) |
| `isRepresentative` | boolean | 예 | 대표 여부 |
| `decision` | string | 예 | 참여 결정; PENDING / ACCEPTED / DECLINED / LEFT / REMOVED_UNPAID |
| `boardingStatus` | string | 예 | 탑승 확인; UNKNOWN / BOARDED / MISSED |
| `stopOrder` | integer / null | 예 | 하차 순서 |

### Party

| 필드 | 타입 | 필수 | 의미·제약 |
| --- | --- | --- | --- |
| `id` | string | 예 | 팟 ID; 형식: `^[1-9][0-9]*$` |
| `status` | string | 예 | 팟 상태; FORMING / CONFIRMING / PREPAYING / READY / RIDING / SETTLING / COMPLETED / CANCELLED |
| `pickupPoint` | PickupPoint | 예 | 해당 DTO 참조 |
| `districtId` | string | 예 | 같은 방향 매칭 권역(구, 구가 없는 시는 시); 형식: `^[1-9][0-9]*$` |
| `districtName` | string | 예 | 권역 이름. 홈 동승 진행 중 카드의 "영통구 방향" |
| `departureAt` | string (date-time) | 예 | 출발 |
| `confirmDeadlineAt` | string (date-time) / null | 예 | 확정 마감; 막차 전 등록은 막차 타러 출발해야 할 시각, 막차 후 등록은 출발 15분 전. 홈 카드·매칭 결과의 "{HH:MM}까지 확정해 주세요"·남은 시간 |
| `representativeMemberId` | string / null | 예 | 대표 참여 ID |
| `myMemberId` | string | 예 | 현재 사용자 참여 ID; 형식: `^[1-9][0-9]*$` |
| `members` | PartyMember[] | 예 | 개인정보를 제한한 멤버 |
| `routeVersion` | integer | 예 | 경로 버전; 최소 1 |
| `taxiCalledAt` | string (date-time) / null | 예 | 대표의 외부 호출 확인 |
| `roomId` | string / null | 예 | 접근 가능한 방 |
| `allowedActions` | string[] | 예 | 서버가 계산한 지금 누를 수 있는 버튼; CONFIRM(확정 마감 전·결정 전) / LEAVE(출발 15분 전까지) / CONFIRM_TAXI_CALL(대표, PREPAYING, 미기록) / RECORD_BOARDING(확정 멤버, 출발 15분 전~출발 1시간 뒤, 미기록) |

### BoardingRequest

| 필드 | 타입 | 필수 | 의미·제약 |
| --- | --- | --- | --- |
| `status` | string | 예 | 본인의 확인; BOARDED / MISSED |

### Room

| 필드 | 타입 | 필수 | 의미·제약 |
| --- | --- | --- | --- |
| `id` | string | 예 | 방 ID; 형식: `^[1-9][0-9]*$` |
| `partyId` | string | 예 | 팟; 형식: `^[1-9][0-9]*$` |
| `status` | string | 예 | 방 상태; OPEN / CLOSED |
| `meetingNote` | string | 예 | 공개 집결 위치 |
| `departureAt` | string (date-time) | 예 | 집결 시각 |
| `verificationCode` | string / null | 예 | 확정 멤버에게만 4자리 코드 |
| `closedAt` | string (date-time) / null | 예 | 종료 |
| `deleteAfter` | string (date-time) / null | 예 | 삭제 예정 |
| `canSendMessage` | boolean | 예 | 전송 가능 여부 |

### MessageCreate

| 필드 | 타입 | 필수 | 의미·제약 |
| --- | --- | --- | --- |
| `clientMessageId` | string (uuid) | 예 | 재전송 중복 방지 UUID |
| `text` | string | 예 | 서버 마스킹 전 입력. v0.4 기준 1~200자(Java 문자열 길이), 공백만 입력 불가, JSON 문자열만 허용 |

### Message

| 필드 | 타입 | 필수 | 의미·제약 |
| --- | --- | --- | --- |
| `id` | string | 예 | 메시지 ID; 형식: `^[1-9][0-9]*$` |
| `roomId` | string | 예 | 방; 형식: `^[1-9][0-9]*$` |
| `senderMemberId` | string / null | 예 | 시스템은 NULL |
| `messageType` | string | 예 | 구분; USER / SYSTEM |
| `body` | string | 예 | 마스킹 후 본문 |
| `createdAt` | string (date-time) | 예 | 작성 시각 |

### ReportCreate

| 필드 | 타입 | 필수 | 의미·제약 |
| --- | --- | --- | --- |
| `targetMemberId` | string | 예 | 신고 대상 참여자; 형식: `^[1-9][0-9]*$` |
| `reasonCode` | string | 예 | 사유; ABUSIVE_LANGUAGE / PRIVACY_REQUEST / NO_SHOW / IDENTITY_MISMATCH / OTHER |
| `messageId` | string | 아니오 | 메시지 신고일 때; 형식: `^[1-9][0-9]*$` |
| `details` | string | 아니오 | 선택적 설명(OTHER도 선택); 마스킹 후 암호화 증거에 포함; 최대 200자(Java 문자열 길이) |

### SafetyReport

| 필드 | 타입 | 필수 | 의미·제약 |
| --- | --- | --- | --- |
| `id` | string | 예 | 신고 ID; 형식: `^[1-9][0-9]*$` |
| `status` | string | 예 | 신고 상태; RECEIVED / REVIEWED / DISMISSED |
| `evidenceDeleteAfter` | string (date-time) | 예 | 신고 접수 시각 +7일. 재시도·다른 신고로 연장하지 않음 |
| `rematchingBlocked` | boolean | 예 | 양쪽 재매칭 차단 적용 |

### AccountInput

| 필드 | 타입 | 필수 | 의미·제약 |
| --- | --- | --- | --- |
| `bankCode` | string | 예 | banks 목록 코드; 최소 1자; 최대 16자 |
| `accountNumber` | string | 예 | 계좌번호; 공백·하이픈 정규화; 최소 1자; 최대 40자 |
| `holderName` | string | 예 | 예금주; 최소 1자; 최대 50자 |

### MaskedAccount

| 필드 | 타입 | 필수 | 의미·제약 |
| --- | --- | --- | --- |
| `id` | string | 예 | 계좌 ID; 형식: `^[1-9][0-9]*$` |
| `purpose` | string | 예 | 용도; PREPAY_RECEIVE / REFUND_RECEIVE |
| `bankCode` | string | 예 | 은행 |
| `maskedAccountNumber` | string | 예 | 마스킹된 계좌번호 |
| `maskedHolderName` | string | 예 | 마스킹된 예금주 |
| `deleteAfter` | string (date-time) / null | 예 | 삭제 예정 |

### RecipientAccount

| 필드 | 타입 | 필수 | 의미·제약 |
| --- | --- | --- | --- |
| `accountId` | string | 예 | 수취 계좌; 형식: `^[1-9][0-9]*$` |
| `bankCode` | string | 예 | 은행 |
| `accountNumber` | string | 예 | 인가된 송금자에게만 원문 |
| `maskedHolderName` | string | 예 | 마스킹 예금주 |
| `purpose` | string | 예 | 계좌 용도; PREPAY_RECEIVE / REFUND_RECEIVE |

### Transfer

| 필드 | 타입 | 필수 | 의미·제약 |
| --- | --- | --- | --- |
| `id` | string | 예 | 수동 이체 ID; 형식: `^[1-9][0-9]*$` |
| `settlementItemId` | string | 예 | 개인 정산; 형식: `^[1-9][0-9]*$` |
| `kind` | string | 예 | 방향; PREPAY / REFUND |
| `payerMemberId` | string | 예 | 송금자; 형식: `^[1-9][0-9]*$` |
| `payeeMemberId` | string | 예 | 수취자; 형식: `^[1-9][0-9]*$` |
| `amountWon` | integer | 예 | 양의 정수 금액; 최소 1 |
| `status` | string | 예 | 수동 확인 상태; PENDING / SENT / RECEIVED / CANCELLED |
| `sentAt` | string (date-time) / null | 예 | 보냈어요 |
| `receivedAt` | string (date-time) / null | 예 | 받았어요 |

### SettlementItem

| 필드 | 타입 | 필수 | 의미·제약 |
| --- | --- | --- | --- |
| `id` | string | 예 | 개인 정산 ID; 형식: `^[1-9][0-9]*$` |
| `memberId` | string | 예 | 팟 참여 ID; 형식: `^[1-9][0-9]*$` |
| `maskedName` | string / null | 예 | 마스킹 이름(예: 김*연). S-06 정산 표의 '이름' |
| `distanceMeters` | integer / null | 예 | 출발지부터 이 멤버 하차까지 누적 거리(m). S-06 '구간'. 핀 없으면 null |
| `isRepresentative` | boolean | 예 | 대표(마지막 하차자) 여부. 화면 '· 대표'·'결제함' 표기 |
| `isMe` | boolean | 예 | 조회자 본인 여부. 화면 '나' 표기·'돌려받을 금액' 카드 |
| `estimatedShareWon` | integer | 예 | 예상 부담; 최소 0 |
| `prepayDueWon` | integer | 예 | 대표는 0; 최소 0 |
| `actualShareWon` | integer / null | 예 | 최종 부담 |
| `refundDueWon` | integer / null | 예 | 대표는 0. 음수이면 추가부담 정책 검토 |
| `status` | string | 예 | 상태; ESTIMATED / PREPAID / REFUND_PENDING / COMPLETED / REVIEW_REQUIRED |
| `transfers` | Transfer[] | 예 | 본인 항목 또는 대표가 관리하는 항목 |

### Settlement

| 필드 | 타입 | 필수 | 의미·제약 |
| --- | --- | --- | --- |
| `id` | string | 예 | 정산 ID; 형식: `^[1-9][0-9]*$` |
| `partyId` | string | 예 | 팟; 형식: `^[1-9][0-9]*$` |
| `estimatedTotalWon` | integer | 예 | 전체 예상 요금; 최소 0 |
| `actualTotalWon` | integer / null | 예 | 실제 전체 요금 |
| `routeVersion` | integer | 예 | 계산 경로 버전; 최소 1 |
| `status` | string | 예 | 상태; ESTIMATED / PREPAYING / AWAITING_FARE / REFUNDING / COMPLETED / REVIEW_REQUIRED |
| `items` | SettlementItem[] | 예 | 대표·일반 멤버 모두 팟 전원 항목; 타인 계좌번호·정확한 경로 제외 |
| `receiptAvailable` | boolean | 예 | 영수증 존재 여부 |
| `finalizedAt` | string (date-time) / null | 예 | 정산 확정 |
| `refundDueAt` | string (date-time) / null | 예 | 환급 기한 |
| `allowedActions` | string[] | 예 | 서버 상태 기준 |

### RoutePatch

| 필드 | 타입 | 필수 | 의미·제약 |
| --- | --- | --- | --- |
| `orderedMemberIds` | string[] | 예 | 현재 계산 대상 전원을 한 번씩 포함; 최소 2개; 최대 4개; 중복 불가 |
| `expectedRouteVersion` | integer | 예 | 수정 전 경로 버전; 최소 1 |

### FareInput

| 필드 | 타입 | 필수 | 의미·제약 |
| --- | --- | --- | --- |
| `actualTotalWon` | integer | 예 | 실제 택시요금; 최소 1 |
| `routeVersion` | integer | 예 | 계산에 쓸 현재 경로 버전; 최소 1 |

### FarePreviewItem

| 필드 | 타입 | 필수 | 의미·제약 |
| --- | --- | --- | --- |
| `memberId` | string | 예 | 멤버; 형식: `^[1-9][0-9]*$` |
| `actualShareWon` | integer | 예 | 계산 부담; 최소 0 |
| `refundDueWon` | integer | 예 | 선송금 − 확정. 양수=환급(대표→멤버), 음수=추가 납부(멤버→대표); 대표 0 |

### FarePreview

| 필드 | 타입 | 필수 | 의미·제약 |
| --- | --- | --- | --- |
| `actualTotalWon` | integer | 예 | 요금; 최소 1 |
| `routeVersion` | integer | 예 | 경로; 최소 1 |
| `items` | FarePreviewItem[] | 예 | 예상 최종 부담 |
| `canFinalize` | boolean | 예 | 확정 가능 |
| `blockingReasons` | string[] | 예 | 예: ADDITIONAL_PAYMENT_POLICY_REQUIRED |

### Receipt

| 필드 | 타입 | 필수 | 의미·제약 |
| --- | --- | --- | --- |
| `available` | boolean | 예 | 첨부 여부 |
| `uploadedAt` | string (date-time) | 예 | 업로드 시각 |
| `contentType` | string | 예 | 허용 이미지; image/jpeg / image/png / image/webp |

### Download

| 필드 | 타입 | 필수 | 의미·제약 |
| --- | --- | --- | --- |
| `downloadUrl` | string (uri) | 예 | 만료되는 비공개 파일 URL |
| `expiresAt` | string (date-time) | 예 | 유효기간 |
| `contentType` | string | 예 | 파일 형식 |

### Notification

| 필드 | 타입 | 필수 | 의미·제약 |
| --- | --- | --- | --- |
| `id` | string | 예 | 알림 ID; 형식: `^[1-9][0-9]*$` |
| `type` | string | 예 | 유형; MATCH_RESULT / PREPAY / REFUND / SYSTEM |
| `partyId` | string / null | 예 | 관련 팟 |
| `title` | string | 예 | 민감정보 없는 제목 |
| `body` | string | 예 | 민감정보 없는 요약 |
| `target` | string | 예 | 앱 내부 이동 경로 |
| `sentAt` | string (date-time) / null | 예 | 발송 시각 |
| `readAt` | string (date-time) / null | 예 | 읽음 시각 |

### Evidence

| 필드 | 타입 | 필수 | 의미·제약 |
| --- | --- | --- | --- |
| `sourceLabel` | string | 예 | 출처 |
| `asOf` | string (date-time) | 예 | 출처 기준/조회 시각 |
| `unit` | string | 예 | 관측 단위 |
| `spatialResolution` | string | 예 | 공간 해상도 |
| `temporalResolution` | string | 예 | 시간 해상도 |
| `kind` | string | 예 | 자료 구분; OBSERVED / ESTIMATED |
| `description` | string | 예 | 해석·보정 방법 |

### DemandCandidate

| 필드 | 타입 | 필수 | 의미·제약 |
| --- | --- | --- | --- |
| `id` | string | 예 | 추정 셀 ID; 형식: `^[1-9][0-9]*$` |
| `runId` | string | 예 | 산출 실행 ID; 형식: `^[1-9][0-9]*$` |
| `universityId` | string | 예 | 대학; 형식: `^[1-9][0-9]*$` |
| `pickupPointId` | string | 예 | 거점; 형식: `^[1-9][0-9]*$` |
| `pickupPointName` | string | 예 | 거점명 |
| `destinationDongId` | string | 예 | 하차 동; 형식: `^[1-9][0-9]*$` |
| `destinationDongName` | string | 예 | 동명 |
| `weekday` | integer | 예 | 요일 0=일요일; 최소 0; 최대 6 |
| `slotStart` | string | 예 | Asia/Seoul HH:mm; 형식: `^(?:[01][0-9]|2[0-3]):(?:00|30)$` |
| `journeyCount` | integer | 예 | 중복 제거 귀가 건수; 최소 0 |
| `failedJourneyCount` | integer | 예 | 실패 경험 귀가; 최종 수단과 겹침; 최소 0 |
| `pooledJourneyCount` | integer | 예 | 탑승 확인; 최소 0 |
| `busSelectedCount` | integer | 예 | 막차 선택; 최소 0 |
| `soloSelectedCount` | integer | 예 | 혼자 택시 선택; 최소 0 |
| `estimatedWeeklyDemand` | number / null | 예 | 주간 추정치 |
| `candidateRank` | integer / null | 예 | 추정 수요 순위 |
| `candidateThreshold` | number / null | 예 | 확정된 후보 임계치 |
| `evidence` | Evidence[] | 예 | 비식별 근거 |

### Contribution

| 필드 | 타입 | 필수 | 의미·제약 |
| --- | --- | --- | --- |
| `runId` | string | 예 | 산출 버전; 형식: `^[1-9][0-9]*$` |
| `myJourneyCount` | integer | 예 | 본인 귀가 반영 수; 최소 0 |
| `visibility` | string | 예 | 공개 표시 가능 여부; VISIBLE / INSUFFICIENT_SAMPLE / NOT_AVAILABLE |
| `candidateId` | string / null | 예 | 공개 가능 후보 ID |
| `remainingToCandidate` | number / null | 예 | 표본 미달이면 반드시 NULL |

### ReportItemInput

| 필드 | 타입 | 필수 | 의미·제약 |
| --- | --- | --- | --- |
| `estimateId` | string | 예 | 공개 가능한 후보; 형식: `^[1-9][0-9]*$` |
| `action` | string | 예 | 조치; SHUTTLE_EXTENSION / CITY_PROPOSAL |

### ReportFilters

| 필드 | 타입 | 필수 | 의미·제약 |
| --- | --- | --- | --- |
| `periodStart` | string (date) | 예 | 산출 기간 시작 |
| `periodEnd` | string (date) | 예 | 산출 기간 끝 |
| `weekday` | integer | 아니오 | 요일; 최소 0; 최대 6 |
| `slotStart` | string | 아니오 | 시간대 HH:mm; 형식: `^(?:[01][0-9]|2[0-3]):(?:00|30)$` |

### StakeholderReportCreate

| 필드 | 타입 | 필수 | 의미·제약 |
| --- | --- | --- | --- |
| `runId` | string | 예 | 동일 산출 버전; 형식: `^[1-9][0-9]*$` |
| `format` | string | 예 | 파일 종류; PDF / IMAGE |
| `filters` | ReportFilters | 예 | 해당 DTO 참조 |
| `items` | ReportItemInput[] | 예 | 리포트 후보 목록; 고정 1페이지 범위에서 제공; 최소 1개 |

### ReportItem

| 필드 | 타입 | 필수 | 의미·제약 |
| --- | --- | --- | --- |
| `estimateId` | string | 예 | 후보; 형식: `^[1-9][0-9]*$` |
| `action` | string | 예 | 조치; SHUTTLE_EXTENSION / CITY_PROPOSAL |
| `displayOrder` | integer | 예 | 순서; 최소 1 |
| `candidate` | DemandCandidate | 예 | 해당 DTO 참조 |

### StakeholderReport

| 필드 | 타입 | 필수 | 의미·제약 |
| --- | --- | --- | --- |
| `id` | string | 예 | 리포트 ID; 형식: `^[1-9][0-9]*$` |
| `runId` | string | 예 | 산출 버전; 형식: `^[1-9][0-9]*$` |
| `format` | string | 예 | 형식; PDF / IMAGE |
| `status` | string | 예 | 상태; DRAFT / GENERATING / READY / FAILED |
| `filters` | ReportFilters | 예 | 해당 DTO 참조 |
| `items` | ReportItem[] | 예 | 출력 시점 비식별 스냅샷 |
| `generatedAt` | string (date-time) / null | 예 | 생성 완료 |
| `downloadAvailable` | boolean | 예 | 파일 준비 여부 |

### Pagination

| 필드 | 타입 | 필수 | 의미·제약 |
| --- | --- | --- | --- |
| `nextCursor` | string / null | 예 | 불투명 커서 |
| `hasMore` | boolean | 예 | 추가 페이지 존재 |

### ApiError

| 필드 | 타입 | 필수 | 의미·제약 |
| --- | --- | --- | --- |
| `error` | object | 예 |  |

`error` 내부: code(string), message(string), details(array of {field: string 또는 null, reason: string}), traceId(string). 모두 필수이며 details는 빈 배열을 허용합니다.

### ReceiptUpload

| 필드 | 타입 | 필수 | 의미·제약 |
| --- | --- | --- | --- |
| `file` | string (binary) | 예 | JPEG/PNG/WebP 1개, 5MiB 이하(제안 제한) |

### DemandSummary

| 필드 | 타입 | 필수 | 의미·제약 |
| --- | --- | --- | --- |
| `journeyCount` | integer | 예 | 공개 가능한 셀만 합산한 journeyCount; 최소 0 |
| `failedJourneyCount` | integer | 예 | 공개 가능한 셀만 합산한 failedJourneyCount; 최소 0 |
| `pooledJourneyCount` | integer | 예 | 공개 가능한 셀만 합산한 pooledJourneyCount; 최소 0 |
| `busSelectedCount` | integer | 예 | 공개 가능한 셀만 합산한 busSelectedCount; 최소 0 |
| `soloSelectedCount` | integer | 예 | 공개 가능한 셀만 합산한 soloSelectedCount; 최소 0 |
| `coverage` | string | 예 | 숨김 셀은 합계에서도 제외; 고정: VISIBLE_CELLS_ONLY |

### DemandMeta

| 필드 | 타입 | 필수 | 의미·제약 |
| --- | --- | --- | --- |
| `runId` | string | 예 | 현재 결과의 산출 버전; 형식: `^[1-9][0-9]*$` |
| `periodStart` | string (date) | 예 | 선택 집계 시작 |
| `periodEnd` | string (date) | 예 | 선택 집계 끝 |
| `modelVersion` | string | 예 | 집계/추정 방식 버전 |
| `summary` | DemandSummary | 예 | 해당 DTO 참조 |
| `nextCursor` | string / null | 예 | 다음 페이지 |
| `hasMore` | boolean | 예 | 다음 페이지 존재 |

### MessagePageMeta

| 필드 | 타입 | 필수 | 의미·제약 |
| --- | --- | --- | --- |
| `nextBeforeId` | string / null | 예 | 과거 페이지 다음 beforeId; 신규 조회에서는 NULL |
| `latestId` | string / null | 예 | 다음 신규 조회의 afterId로 사용할 가장 큰 반환 ID |
| `hasMore` | boolean | 예 | 요청 방향으로 다음 페이지 존재 |

### 응답 예시

정산 조회·확정 응답에는 호출자 권한과 무관하게 팟 전원의 개인 항목이 `items` 배열로 들어갑니다(타인 계좌번호·정확한 경로는 제외). 예시 날짜·만료 시각은 정책의 확정값이 아닙니다.

### GET /auth/session · 200 응답 예시

```json
{
  "data": {
    "user": {
      "id": "101",
      "universityId": "1",
      "email": "student@university.example",
      "role": "STUDENT",
      "status": "ACTIVE"
    },
    "expiresAt": "2026-11-06T02:00:00+09:00",
    "csrfToken": "example-csrf-token"
  }
}
```

### GET /parties/{partyId}/settlement · 200 응답 예시

조회자가 대표든 일반 멤버든 **팟 전원의 개인 항목이 `items` 배열로** 내려온다(타인 계좌번호·정확한 경로는 제외). 아래는 대표(503)와 일반 멤버(501)로 이뤄진 팟 예시.

```json
{
  "data": {
    "id": "901",
    "partyId": "401",
    "estimatedTotalWon": 30000,
    "actualTotalWon": 30000,
    "routeVersion": 2,
    "status": "REFUNDING",
    "items": [
      {
        "id": "10001",
        "memberId": "501",
        "maskedName": "김*연",
        "distanceMeters": 8000,
        "isRepresentative": false,
        "isMe": true,
        "estimatedShareWon": 5000,
        "prepayDueWon": 6000,
        "actualShareWon": 5000,
        "refundDueWon": 1000,
        "status": "REFUND_PENDING",
        "transfers": [
          {
            "id": "801",
            "settlementItemId": "10001",
            "kind": "PREPAY",
            "payerMemberId": "501",
            "payeeMemberId": "503",
            "amountWon": 6000,
            "status": "RECEIVED",
            "sentAt": "2026-11-05T23:22:00+09:00",
            "receivedAt": "2026-11-05T23:23:00+09:00"
          },
          {
            "id": "802",
            "settlementItemId": "10001",
            "kind": "REFUND",
            "payerMemberId": "503",
            "payeeMemberId": "501",
            "amountWon": 1000,
            "status": "PENDING",
            "sentAt": null,
            "receivedAt": null
          }
        ]
      },
      {
        "id": "10002",
        "memberId": "503",
        "maskedName": "이*준",
        "distanceMeters": 16000,
        "isRepresentative": true,
        "isMe": false,
        "estimatedShareWon": 25000,
        "prepayDueWon": 0,
        "actualShareWon": 25000,
        "refundDueWon": 0,
        "status": "COMPLETED",
        "transfers": []
      }
    ],
    "receiptAvailable": true,
    "finalizedAt": "2026-11-06T00:10:00+09:00",
    "refundDueAt": "2026-11-13T00:10:00+09:00",
    "allowedActions": []
  }
}
```

### POST /parties/{partyId}/settlement/finalize · 200 응답 예시

```json
{
  "data": {
    "id": "901",
    "partyId": "401",
    "estimatedTotalWon": 30000,
    "actualTotalWon": 30000,
    "routeVersion": 2,
    "status": "REFUNDING",
    "items": [
      {
        "id": "10001",
        "memberId": "501",
        "maskedName": "김*연",
        "distanceMeters": 8000,
        "isRepresentative": false,
        "isMe": false,
        "estimatedShareWon": 5000,
        "prepayDueWon": 6000,
        "actualShareWon": 5000,
        "refundDueWon": 1000,
        "status": "REFUND_PENDING",
        "transfers": [
          {
            "id": "801",
            "settlementItemId": "10001",
            "kind": "PREPAY",
            "payerMemberId": "501",
            "payeeMemberId": "503",
            "amountWon": 6000,
            "status": "RECEIVED",
            "sentAt": "2026-11-05T23:22:00+09:00",
            "receivedAt": "2026-11-05T23:23:00+09:00"
          },
          {
            "id": "802",
            "settlementItemId": "10001",
            "kind": "REFUND",
            "payerMemberId": "503",
            "payeeMemberId": "501",
            "amountWon": 1000,
            "status": "PENDING",
            "sentAt": null,
            "receivedAt": null
          }
        ]
      },
      {
        "id": "10002",
        "memberId": "502",
        "maskedName": "한*윤",
        "distanceMeters": 12000,
        "isRepresentative": false,
        "isMe": false,
        "estimatedShareWon": 8750,
        "prepayDueWon": 10500,
        "actualShareWon": 8750,
        "refundDueWon": 1750,
        "status": "REFUND_PENDING",
        "transfers": [
          {
            "id": "803",
            "settlementItemId": "10002",
            "kind": "PREPAY",
            "payerMemberId": "502",
            "payeeMemberId": "503",
            "amountWon": 10500,
            "status": "RECEIVED",
            "sentAt": "2026-11-05T23:22:00+09:00",
            "receivedAt": "2026-11-05T23:23:00+09:00"
          },
          {
            "id": "804",
            "settlementItemId": "10002",
            "kind": "REFUND",
            "payerMemberId": "503",
            "payeeMemberId": "502",
            "amountWon": 1750,
            "status": "PENDING",
            "sentAt": null,
            "receivedAt": null
          }
        ]
      },
      {
        "id": "10003",
        "memberId": "503",
        "maskedName": "이*준",
        "distanceMeters": 16000,
        "isRepresentative": true,
        "isMe": true,
        "estimatedShareWon": 16250,
        "prepayDueWon": 0,
        "actualShareWon": 16250,
        "refundDueWon": 0,
        "status": "COMPLETED",
        "transfers": []
      }
    ],
    "receiptAvailable": true,
    "finalizedAt": "2026-11-06T00:10:00+09:00",
    "refundDueAt": "2026-11-13T00:10:00+09:00",
    "allowedActions": []
  }
}
```

### GET /rooms/{roomId}/messages · 200 응답 예시

```json
{
  "data": [
    {
      "id": "701",
      "roomId": "601",
      "senderMemberId": "501",
      "messageType": "USER",
      "body": "집결 장소에 도착했어요.",
      "createdAt": "2026-11-05T23:20:00+09:00"
    }
  ],
  "meta": {
    "nextBeforeId": null,
    "latestId": "701",
    "hasMore": false
  }
}
```

### GET /demand/candidates · 200 응답 예시

```json
{
  "data": [
    {
      "id": "1501",
      "runId": "1401",
      "universityId": "1",
      "pickupPointId": "11",
      "pickupPointName": "학교 정문 거점",
      "destinationDongId": "31",
      "destinationDongName": "예시동",
      "weekday": 4,
      "slotStart": "23:30",
      "journeyCount": 12,
      "failedJourneyCount": 4,
      "pooledJourneyCount": 6,
      "busSelectedCount": 4,
      "soloSelectedCount": 2,
      "estimatedWeeklyDemand": 40,
      "candidateRank": 2,
      "candidateThreshold": 30,
      "evidence": [
        {
          "sourceLabel": "서비스 귀가 집계",
          "asOf": "2026-11-05T06:00:00+09:00",
          "unit": "귀가 건",
          "spatialResolution": "거점 × 행정동",
          "temporalResolution": "요일 × 30분",
          "kind": "OBSERVED",
          "description": "재등록을 동일 귀가로 중복 제거"
        }
      ]
    }
  ],
  "meta": {
    "runId": "1401",
    "periodStart": "2026-10-29",
    "periodEnd": "2026-11-04",
    "modelVersion": "proposal-v1",
    "summary": {
      "journeyCount": 12,
      "failedJourneyCount": 4,
      "pooledJourneyCount": 6,
      "busSelectedCount": 4,
      "soloSelectedCount": 2,
      "coverage": "VISIBLE_CELLS_ONLY"
    },
    "nextCursor": null,
    "hasMore": false
  }
}
```

## 6. 오류 계약

```json
{
  "error": {
    "code": "PREPAY_NOT_COMPLETED",
    "message": "아직 수령 확인이 끝나지 않은 선송금이 있어요.",
    "details": [],
    "traceId": "example-trace-001"
  }
}
```

프런트엔드는 문구가 아닌 `error.code`로 분기합니다. 401은 재인증, 412는 최신 자원 재조회, 429는 Retry-After를 따릅니다. 503 POLICY_NOT_CONFIGURED는 기획/운영 설정이 필요한 기능이 준비되지 않았다는 뜻이며 임의 기본 정책으로 진행하지 않습니다.

| HTTP | 코드 | 의미 |
| --- | --- | --- |
| 400 | `INVALID_REQUEST` | 잘못된 JSON·상호 배타 파라미터 |
| 401 | `INVALID_CREDENTIALS` | 관계자 이메일/비밀번호 오류 |
| 401 | `UNAUTHENTICATED` | 세션 없음/만료 |
| 403 | `ACCOUNT_PURPOSE_NOT_ALLOWED` | 계좌 용도와 역할 불일치 |
| 403 | `ACCOUNT_SUSPENDED` | 새 동승 이용이 제한된 계정 |
| 403 | `ACCOUNT_UNAVAILABLE` | 탈퇴 등 인증할 수 없는 계정 |
| 403 | `FORBIDDEN` | 역할·계정 상태상 허용되지 않음 |
| 403 | `SCOPE_NOT_ALLOWED` | 학교·기간 열람 범위 위반 |
| 404 | `ACCOUNT_NOT_FOUND` | 검증한 이메일의 학생 계정 없음 |
| 404 | `RECEIPT_NOT_FOUND` | 영수증 없음 |
| 404 | `RESOURCE_NOT_FOUND` | 없는 자원 또는 해당 사용자에게 공개되지 않는 자원 |
| 409 | `ACCOUNT_ALREADY_EXISTS` | 검증한 이메일의 계정 존재 |
| 409 | `ACCOUNT_LOCKED` | 진행/완료된 송금에 연결된 계좌 변경 불가 |
| 409 | `ACCOUNT_NOT_REGISTERED` | 수취 계좌 미등록 |
| 409 | `ACTIVE_JOURNEY_CONFLICT` | 다른 귀가에 진행 중인 동승이 존재 |
| 409 | `ACTIVE_REQUEST_EXISTS` | 현재 활성 동승 신청 존재 |
| 409 | `ADDITIONAL_PAYMENT_POLICY_REQUIRED` | 개인 부담이 선송금을 초과하여 검토 필요 |
| 409 | `BOARDING_ALREADY_RECORDED` | 기록된 탑승 상태를 다른 값으로 덮을 수 없음 |
| 409 | `BOARDING_NOT_ALLOWED` | 집결/참여 상태상 탑승 확인 불가 |
| 409 | `BOARDING_POLICY_REQUIRED` | 미탑승/불명 탑승자의 분담 정책 필요 |
| 409 | `CANCELLATION_DEADLINE_PASSED` | 출발 15분 전 취소 마감 경과 |
| 409 | `CONFIRMATION_DEADLINE_PASSED` | 동승 확정 마감 경과 |
| 409 | `DEMAND_NOT_READY` | 요청한 기간의 성공 산출 없음 |
| 409 | `FUNDS_ALREADY_IN_FLIGHT` | 송금 진행 중인 팟의 취소 정책 필요 |
| 409 | `IDEMPOTENCY_CONFLICT` | 같은 멱등키에 다른 요청 본문 |
| 409 | `INVALID_ROUTE_DISTANCE` | 계산 가능한 양의 전체 경로 거리 없음 |
| 409 | `INVALID_STATE` | 현재 상태에서 수행 불가 |
| 409 | `JOURNEY_ALREADY_RESOLVED` | 이미 결과가 확정된 귀가 |
| 409 | `PARTY_LEAVE_REQUIRED` | 매칭된 팟 이탈 처리가 먼저 필요 |
| 409 | `PREPAY_NOT_COMPLETED` | 유효 멤버의 선송금 수령 확인 미완료 |
| 409 | `REPORT_CONTENT_UNAVAILABLE` | 출력 시점에 사용할 수 없는 후보 |
| 409 | `REPORT_NOT_READY` | 출력 파일이 준비되지 않음 |
| 409 | `REPORT_ALREADY_EXISTS` | 같은 방에서 동일 신고자가 이미 신고한 대상 |
| 409 | `REPRESENTATIVE_CHANGE_REQUIRES_POLICY` | 대표 교체와 기수령금 처리 정책 필요 |
| 409 | `REQUEST_ALREADY_MATCHED` | 이미 팟에 배정된 신청 |
| 409 | `ROOM_CLOSED` | 닫힌 방에 쓰기 불가 |
| 409 | `ROOM_NOT_READY` | 채팅방 오픈 조건 미충족 |
| 409 | `ROUTE_VERSION_CONFLICT` | 하차 경로 버전 불일치 |
| 409 | `SETTLEMENT_FINALIZED` | 확정된 정산은 재입력/순서 변경 불가 |
| 409 | `SETTLEMENT_NOT_READY` | 정산 초안 미생성 |
| 409 | `TRANSFER_CANCELLED` | 취소된 이체의 확인 불가 |
| 409 | `UNSETTLED_RIDE_RESTRICTION` | 확정 정책에 따른 미정산 이용 제한 |
| 409 | `VERIFICATION_USED` | 인증 코드/증표 이미 소비 |
| 410 | `ACCOUNT_EXPIRED` | 수취 계좌 삭제/노출 기간 만료 |
| 410 | `RECEIPT_EXPIRED` | 영수증 보관 만료 |
| 410 | `REPORT_FILE_EXPIRED` | 보관 기한이 지난 리포트 파일 |
| 410 | `ROOM_ACCESS_EXPIRED` | 방 접근/보관 기간 만료 |
| 410 | `VERIFICATION_EXPIRED` | 인증 코드/증표 만료 |
| 412 | `PRECONDITION_FAILED` | If-Match가 현재 ETag와 다름 |
| 413 | `FILE_TOO_LARGE` | 파일 제한 크기 초과 |
| 415 | `INVALID_FILE_TYPE` | 허용되지 않은 파일 형식 |
| 422 | `EMAIL_DOMAIN_NOT_ALLOWED` | 해당 학교 허용 이메일 도메인이 아님 |
| 422 | `FARE_LIMIT_EXCEEDED` | 예상 전체 요금의 1.2배 상한 초과 |
| 422 | `INVALID_DEPARTURE_SLOT` | 허용되지 않은 출발 슬롯 |
| 422 | `INVALID_GENDER_CODE` | 서버가 승인하지 않은 성별 코드 |
| 422 | `INVALID_REPORT_TARGET` | 본인·타 팟·타 메시지 대상 신고 |
| 422 | `INVALID_STOP_ORDER` | 누락/중복/타 팟 멤버가 포함된 순서 |
| 422 | `INVALID_VERIFICATION_CODE` | 인증 코드 불일치 |
| 422 | `LOCATION_UNRESOLVABLE` | 핀의 행정동/권역을 결정할 수 없음 |
| 422 | `MESSAGE_REJECTED` | 마스킹/내용 검증을 통과하지 못한 메시지 |
| 422 | `MIXED_DEMAND_RUN` | 여러 산출 실행의 후보를 혼합 |
| 422 | `REPORT_FILTER_MISMATCH` | 후보와 선택 필터가 불일치 |
| 422 | `REPORT_LAYOUT_LIMIT` | 고정 1페이지 템플릿 수용 범위 초과 |
| 422 | `SUPPRESSED_CANDIDATE` | 표본 미달/열람 불가 후보는 보고서에 추가 불가 |
| 422 | `VALIDATION_ERROR` | 요청 필드·형식·허용 값 오류 |
| 428 | `PRECONDITION_REQUIRED` | 필수 If-Match 누락 |
| 429 | `RATE_LIMITED` | 요청 속도 제한; Retry-After 확인 |
| 500 | `INTERNAL_ERROR` | 예상하지 못한 서버 오류 |
| 503 | `EMAIL_DELIVERY_UNAVAILABLE` | 메일 발송 접수 실패 |
| 503 | `POLICY_NOT_CONFIGURED` | 필요한 기획 정책/운영 설정이 아직 준비되지 않음 |
| 503 | `UPSTREAM_UNAVAILABLE` | 외부 경로·교통 데이터 일시 장애 |

## 7. 기획 확정이 필요한 항목

| 항목 | 이 명세의 처리 |
| --- | --- |
| 학생 재로그인·세션 | 이메일 OTP 학생 로그인 구현. 기본 서버 유휴 만료·쿠키 유지 30일(SESSION_TIMEOUT으로 변경 가능). 기존 이메일 인증 제한 유지: 재발송 1분, 하루 10회(한국 시간 자정 초기화), 실패 5회 시 10분 제한. 학생 신규/기존 분기 안내는 프런트와 연동 시 정리 |
| 정지 계정의 정산 | 새 동승 차단과 기존 송금·환급 확인용 제한 접근 유지 제안. 예외 권한 확정 필요 |
| 성별 코드·수정 | 가입은 v0.3의 FEMALE/여성, MALE/남성 사용. 동성 매칭은 켜기·끄기 모두 허용하도록 확인됨(기본 true). v0.3의 성별 수정은 후속 프로필 API에서 반영할 항목 |
| 동승 시간·결과 마감 | 30분 슬롯은 확정. 운영 시간과 결과 안내 산정식은 서버 설정으로 확정 필요. v0.2의 시간 범위·수식을 자동 적용하지 않음 |
| 팟 확정·채팅 오픈 | v0.4 기준 첫 확정 시 내부 방 생성, 확정한 멤버부터 입장. 대표는 계좌 등록 후 입장. 마감 미확정 제외·해산은 기존 팟 API에서 처리 |
| 취소 경계·기수령금 | 원본의 출발 15분 전 취소 적용. 정확한 경계 포함 여부는 제안. 선송금 시작 이후 취소·해산·대표 교체는 정책 확정 전 자동 처리 보류 |
| 출발 +5분 미입금 제외 | 원본 규칙 유지. 제외 후 1명만 남거나 이미 보낸 돈이 있는 경우 재분담·환급 방식 확정 필요 |
| 미탑승·음수 환급 | 자동 전액 환급·추가 송금·다수결 정책을 만들지 않음. PREVIEW는 확정 불가 사유, FINALIZE는 검토 상태로 분기 |
| 수동 송금 확인 순서 | 수취자가 송금자보다 먼저 입금을 확인하는 경우 허용 제안. 실제 은행 검증과 구분 |
| 채팅·계좌·영수증 삭제 | v0.4 기준 max(출발+1시간, 대표 정산 확정) 종료 및 계좌 삭제. 정산 미확정이면 연장. 신고 증거는 기획 질문 v3 기준 신고 시점까지의 방 전체 대화 사본을 신고별 접수 +7일에 정리하고 차단 유지. 일반 메시지 물리 삭제·영수증 보관 기간은 별도 정책 |
| 계좌 재사용 | 장기 서버 보관 API 없음. ‘다음에도 사용’의 기기 보관·동의 범위 확정 필요 |
| 미정산 제한·제재 | 신규 등록 제한 대상/기간, 7일 지연 정지의 기산점·해제 조건 확정 필요 |
| 최소 표본·AI 순위 | ‘등록 5건’의 정확한 단위 확인 필요. 현재 임시 제안은 중복 제거 귀가 기준. 원본의 추정 수요 순위를 사용하고 복합 점수는 보류 |
| 관계자 범위 | 초기 제안은 본인 대학의 집계와 본인이 생성한 리포트. 타 대학 집계·팀 리포트 공유 권한 확정 필요 |
| 파일·화면 제한 | 채팅 메시지는 v0.4의 최대 200자 적용. 이미지 종류/5MiB와 리포트 한 페이지의 후보 수용량은 구현 제안이며 실제 템플릿 기준으로 확정 |
| 외부 자료 | 버스 경로·요금·추정 모델·원천 해상도·갱신 주기 검증 필요. 실제 연동 완료를 전제하지 않음 |

## 8. 구현·검토 체크

| 검증 | 결과 |
| --- | --- |
| ERD 대응 | 25개 테이블 모두 API 또는 서버 내부 작업에 대응 |
| 인터페이스 | 45개 경로, 50개 operationId 중복 없음 |
| 자료형 | 63개 DTO, ID 문자열·금액 정수·NULL 구분 |
| 참조 | OpenAPI 내부 참조 556개와 경로 파라미터 대응 확인 |
| 예시 | JSON 요청 예시 18개·성공 응답 예시 49개를 필수 필드·타입·범위·허용 값 기준으로 대조. 파일 업로드는 multipart로 별도 정의 |
| 권한 | 비공개 API 인증, 변경 요청 CSRF, 본인/대표/송금자/수취자/리포트 생성자 구분 |
| 정산 | 클라이언트 계산액 신뢰 금지, 서버 재계산·상한·합계·대표 0원·음수 환급 검토 규칙 반영 |
| 개인정보 | 프로필·팟·정산·계좌·관계자 DTO를 분리. DB 암호화 원문/해시/파일 내부 키를 일반 응답에 노출하지 않음 |
| 미수행 | 실제 서버·메일·교통·은행·파일 저장소 연동 테스트는 서버 구현 후 필요. 은행 송금과 택시 호출은 이 API가 실행하지 않음 |

**구현 순서 제안:** 기준정보·가입 → 귀가/매칭 → 채팅 → 선송금/정산 → 수요/리포트. 각 단계에서 상태 전이·소유권·동시 요청을 서버 통합 테스트로 검증합니다. API 경로가 있다고 해당 정책이 승인된 것은 아니며, 7절의 보류 항목은 구현 전에 기획과 확정합니다.
