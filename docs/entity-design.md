# 엔티티·도메인 설계 컨벤션

작성일: 2026-11-05 · 기준: `docs/erd.md` (25개 테이블·5개 영역)

ERD를 코드로 옮기면서 채택한 규칙입니다. 코드만 봐서는 의도를 알기 어려운 결정 위주로 정리했습니다. 바꾸고 싶은 항목이 있으면 팀에서 합의 후 수정합니다.

## 패키지 구조

- `com.yongyong.owlly.domain.<도메인>.entity` 아래에 엔티티와 해당 도메인 전용 enum을 둡니다.
- 도메인 11개: `university`, `user`, `verification`, `region`, `journey`, `party`, `chat`, `safety`, `notification`, `settlement`, `demand`.
- 공통 시각 필드는 `common/entity/BaseTimeEntity`, Auditing 활성화는 `config/JpaAuditingConfig`.

## 엔티티 규칙

| 항목 | 규칙 | 이유 |
| --- | --- | --- |
| **FK** | `@ManyToOne` 연관관계 대신 `Long xxxId` 컬럼으로 저장 | 도메인 간 결합을 끊어 aggregate 단위로 독립. 패키지 밖 엔티티를 import하지 않음 |
| PK | `@GeneratedValue(IDENTITY)` (bigint). `student_profiles`만 `user_id` 공유 PK | ERD의 bigint ID 기준 |
| 공통 필드 | 모든 엔티티가 `BaseTimeEntity` 상속 → `created_at`/`updated_at` 자동 | ERD 공통 규칙 |
| 롬복 | `@Getter` + `protected` 기본 생성자 + `@Builder`(private all-args)만 | 초기 세팅 범위. 상태 변경 메서드는 아직 없음 |
| enum | `@Enumerated(EnumType.STRING)` | 순서 변경에 안전 |
| 금액 | `int`/`Integer` (원 단위 정수) | ERD 금액 규칙 |
| 시각 | `Instant` (timestamptz, UTC 저장) | ERD 시각 규칙 |

### FK를 Long으로 둔 것의 트레이드오프
- JPA 연관 탐색·페치조인을 쓸 수 없습니다. 조회 시 ID로 직접 조인하거나 서비스 레이어에서 조합합니다.
- 팀이 `@ManyToOne` 방식을 선호하면 전환 가능합니다.

## DB 제약 — ddl-auto로 자동 생성되는 것 / 아닌 것

`application.yaml`은 `ddl-auto: update`입니다.

**자동 반영됨**
- 단일 컬럼 UNIQUE (`@Column(unique = true)`)
- 복합 UNIQUE (`@Table(uniqueConstraints = ...)`) — party_stops, settlement_items, transfers, user_blocks, room_accounts, demand_estimates, stakeholder_report_items

**자동 반영 안 됨 → 별도 마이그레이션 필요** (ERD 6절)
- `CHECK(user_low_id < user_high_id)` (user_blocks)
- 부분 인덱스 / 조건부 UNIQUE: 활성 상태 한정 `UNIQUE(journey_id)`(ride_requests), NULL 제외 `UNIQUE(representative_member_id)` 등
- 성능용 복합 인덱스 (ERD 「제안 인덱스」 표 전체)
- FK 삭제 규칙 (`SET NULL` 등): transfers.receiver_account_id, safety_reports.message_id, user_blocks.source_report_id

## 미구현 / 보류 항목

| 항목 | 현재 상태 | 비고 |
| --- | --- | --- |
| 암호화 필드 | 실명·좌표·계좌·증거를 `text` 컬럼으로만 둠 | 실제 암호화 변환기(`AttributeConverter` 등) 미구현 |
| JSON 필드 | `@JdbcTypeCode(SqlTypes.JSON)` + `String`으로 jsonb 매핑 | payload, input_sources, evidence, filters_snapshot, candidate_snapshot |
| `Gender` enum | `MALE`/`FEMALE` placeholder | ERD 9절: 허용 값 **기획 확정 필요** |
| Repository/Service/Controller | 없음 | 초기 엔티티 세팅 범위 |
