---
title: Owlly API 진행 현황
---

# 🦉 Owlly API 진행 현황

전체 **50개** 엔드포인트 · 담당자 / 완료 여부 · 마지막 갱신 **2026-11-09**

| 구분 | 수 |
|---|---|
| 전체 API | 50 |
| ✅ 완료 | 4 |
| ⬜ 미완료 | 46 |

**담당 배정:** 인증·채팅 → 강나연 · 귀가·매칭 → 박기범 · 정산·수요 → 안시현

---

## 인증·기준정보 — 강나연

| ID | Method | 엔드포인트 | 설명 | 담당자 | 상태 |
|---|---|---|---|---|---|
| API-01 | GET | `/universities` | 참여 대학·인증 도메인 조회 | 강나연 | ⬜ 미완료 |
| API-02 | GET | `/profile-options` | 프로필 선택값 조회 | 강나연 | ⬜ 미완료 |
| API-03 | GET | `/banks` | 수취 계좌 은행 목록 | 강나연 | ⬜ 미완료 |
| API-04 | GET | `/pickup-points` | 고정 출발 거점 목록 | 강나연 ※ | ✅ 완료 |
| API-05 | POST | `/auth/email-verifications` | 학교 이메일 인증번호 발급·재발급 | 강나연 | ⬜ 미완료 |
| API-06 | POST | `/auth/email-verifications/{id}/verify` | 이메일 코드 검증 | 강나연 | ⬜ 미완료 |
| API-07 | POST | `/auth/student-registrations` | 학생 가입·프로필 생성 | 강나연 | ⬜ 미완료 |
| API-08 | POST | `/auth/student-sessions` | 학생 재로그인 | 강나연 | ⬜ 미완료 |
| API-09 | POST | `/auth/staff-sessions` | 관계자 로그인 | 강나연 | ⬜ 미완료 |
| API-10 | GET | `/auth/session` | 현재 계정·세션 확인 | 강나연 | ⬜ 미완료 |
| API-11 | DELETE | `/auth/session` | 로그아웃 | 강나연 | ⬜ 미완료 |

## 프로필·귀가 — 박기범

| ID | Method | 엔드포인트 | 설명 | 담당자 | 상태 |
|---|---|---|---|---|---|
| API-12 | GET | `/me/profile` | 내 프로필 조회 | 박기범 | ⬜ 미완료 |
| API-13 | PATCH | `/me/profile` | 내 프로필 수정 | 박기범 | ⬜ 미완료 |
| API-14 | POST | `/travel-options` | 막차·동승·혼자 택시 비교 | 박기범 | ⬜ 미완료 |
| API-15 | POST | `/journeys` | 한 번의 귀가 의도 생성 | 박기범 | ⬜ 미완료 |
| API-16 | GET | `/me/journeys` | 내 귀가 기록 조회 | 박기범 | ✅ 완료 |
| API-17 | PUT | `/journeys/{id}/choice` | 막차 또는 혼자 택시 선택 기록 | 박기범 | ⬜ 미완료 |

## 동승·매칭 — 박기범

| ID | Method | 엔드포인트 | 설명 | 담당자 | 상태 |
|---|---|---|---|---|---|
| API-18 | GET | `/matching-slots` | 조건별 출발 슬롯·대기 수 조회 | 박기범 | ⬜ 미완료 |
| API-19 | POST | `/journeys/{id}/ride-requests` | 동승 신청·실패 후 재등록 | 박기범 | ⬜ 미완료 |
| API-20 | GET | `/ride-requests/{id}` | 신청·매칭 결과 조회 | 박기범 | ✅ 완료 |
| API-21 | POST | `/ride-requests/{id}/cancel` | 대기 신청 취소 | 박기범 | ✅ 완료 |
| API-22 | GET | `/parties/{id}` | 팟·멤버·대표·다음 행동 조회 | 박기범 | ⬜ 미완료 |
| API-23 | POST | `/parties/{id}/confirm` | 본인 팟 확정 | 박기범 | ⬜ 미완료 |
| API-24 | POST | `/parties/{id}/leave` | 팟 거절·이탈 | 박기범 | ⬜ 미완료 |
| API-25 | POST | `/parties/{id}/taxi-call-confirmation` | 대표의 외부 택시 호출 확인 | 박기범 | ⬜ 미완료 |
| API-26 | POST | `/parties/{id}/boarding` | 본인 탑승·미탑승 기록 | 박기범 | ⬜ 미완료 |

## 채팅·신고 — 강나연

| ID | Method | 엔드포인트 | 설명 | 담당자 | 상태 |
|---|---|---|---|---|---|
| API-27 | GET | `/parties/{id}/room` | 팟 채팅방·확인 코드 조회 | 강나연 | ⬜ 미완료 |
| API-28 | GET | `/rooms/{id}/messages` | 채팅 이력·새 메시지 조회 | 강나연 | ⬜ 미완료 |
| API-29 | POST | `/rooms/{id}/messages` | 채팅 메시지 전송 | 강나연 | ⬜ 미완료 |
| API-30 | POST | `/rooms/{id}/reports` | 멤버·메시지 신고 | 강나연 | ⬜ 미완료 |

## 계좌·정산 — 안시현

| ID | Method | 엔드포인트 | 설명 | 담당자 | 상태 |
|---|---|---|---|---|---|
| API-31 | PUT | `/rooms/{id}/accounts/{purpose}` | 내 선송금·환급 수취 계좌 등록 | 안시현 | ⬜ 미완료 |
| API-32 | GET | `/transfers/{id}/recipient-account` | 송금할 수취 계좌 조회 | 안시현 | ⬜ 미완료 |
| API-33 | GET | `/parties/{id}/settlement` | 예상·선송금·최종 정산 조회 | 안시현 | ⬜ 미완료 |
| API-34 | POST | `/parties/{id}/settlement/receipt` | 대표 결제 화면 첨부 | 안시현 | ⬜ 미완료 |
| API-35 | GET | `/parties/{id}/settlement/receipt` | 참여자 결제 화면 열기 | 안시현 | ⬜ 미완료 |
| API-36 | PATCH | `/parties/{id}/settlement/route` | 정산 전 실제 하차 순서 수정 | 안시현 | ⬜ 미완료 |
| API-37 | POST | `/parties/{id}/settlement/preview` | 실제 요금 분담·환급 미리보기 | 안시현 | ⬜ 미완료 |
| API-38 | POST | `/parties/{id}/settlement/finalize` | 실제 요금·개인 부담·환급 확정 | 안시현 | ⬜ 미완료 |
| API-39 | POST | `/transfers/{id}/sent` | 송금자의 '보냈어요' | 안시현 | ⬜ 미완료 |
| API-40 | POST | `/transfers/{id}/received` | 수취자의 '받았어요' | 안시현 | ⬜ 미완료 |

## 알림 — 미정

| ID | Method | 엔드포인트 | 설명 | 담당자 | 상태 |
|---|---|---|---|---|---|
| API-41 | GET | `/me/notifications` | 내 알림 조회 | 미정 | ⬜ 미완료 |
| API-42 | POST | `/me/notifications/{id}/read` | 알림 읽음 처리 | 미정 | ⬜ 미완료 |

## 수요 지도 — 안시현

| ID | Method | 엔드포인트 | 설명 | 담당자 | 상태 |
|---|---|---|---|---|---|
| API-43 | GET | `/demand/candidates` | 수요 지도·노선 후보 목록 | 안시현 | ⬜ 미완료 |
| API-44 | GET | `/demand/candidates/{id}` | 노선 구간·근거 지표 상세 | 안시현 | ⬜ 미완료 |
| API-45 | GET | `/me/demand-contribution` | 내 귀가 반영 횟수·후보 여부 | 안시현 | ⬜ 미완료 |

## 관계자 리포트 — 미정

| ID | Method | 엔드포인트 | 설명 | 담당자 | 상태 |
|---|---|---|---|---|---|
| API-46 | POST | `/staff/reports` | 후보·조치로 리포트 초안 생성 | 미정 | ⬜ 미완료 |
| API-47 | GET | `/staff/reports` | 내 리포트 목록 | 미정 | ⬜ 미완료 |
| API-48 | GET | `/staff/reports/{id}` | 리포트 미리보기·생성 상태 조회 | 미정 | ⬜ 미완료 |
| API-49 | POST | `/staff/reports/{id}/exports` | 리포트 PDF·이미지 생성 | 미정 | ⬜ 미완료 |
| API-50 | GET | `/staff/reports/{id}/download` | 완성된 리포트 다운로드 URL | 미정 | ⬜ 미완료 |

---

### 참고

- **알림(API-41~42)**, **관계자 리포트(API-46~50)** 는 배정이 없어 **미정** 으로 두었습니다.
- 완료 표시는 `develop` 머지 기준(API-04·16·20·21)입니다.
- ※ **API-04(거점)** 는 섹션상 강나연 담당이나 실제 구현은 박기범(gibeom00)이 머지했습니다 — 담당 재확인 필요.
- 상태 수정은 이 파일(`docs/status.md`)의 **상태** 칸을 고치고 `develop`에 머지하면 GitHub Pages에 자동 반영됩니다.
