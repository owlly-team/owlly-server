# 수요 산출 공공데이터 (출처·접근·연동 로드맵)

수요 산출(AI-2)의 입력과 A-02 근거 지표에 쓰는 공공데이터 4종. 2026-11 웹 확인 기준으로 **실제 출처·접근 방법**을 정리했다.
현재 파이프라인(`domain/demand/pipeline`)은 **서비스 집계는 실데이터**, **공공데이터 보정·근거 지표는 추정치(라이브 연동 전)**, **기존 공급은 실제 심야 노선 기반**으로 동작한다.

## 1. 기존 공급 — 실제 심야 노선 (이미 반영됨)

웹 확인 결과 Figma "기존 공급"은 실제 노선과 일치한다. `DemandEstimator.realExistingSupply()` 가 도착동 이름으로 매칭한다.

| 노선 | 운행 | 구간 | 매칭 동 |
| --- | --- | --- | --- |
| **N1** | 심야 1일 1회 | 용인터미널~처인구청~명지대~시청·용인대역~강남대~기흥구청~신갈역~구성역 | 처인구 → "N1 하루 1회" |
| **1101N** | 평일 심야 2회(강남역 01:00·02:00 출발) | 단국대(수지구 죽전동)~강남역 | 수지구/죽전 → "1101N 평일 2회" |

출처: thewiki/나무위키 1101·N1 노선, GBIS 심야버스 현황. (키 불필요 — 노선 사실 정보)

## 2. 라이브 연동이 남은 3종 (인증키·파일 필요)

| 데이터 | 출처 | 접근 | 쓰임 | 상태 |
| --- | --- | --- | --- | --- |
| 경기도 시군별 버스 이용객수 | 경기교통DB(GITS) / data.go.kr **15121882** (OpenAPI) | data.go.kr 무료 인증키 | 23시 이후 버스 끊김 → 이용자 편향 보정, A-02 "심야 버스 이용객" | 키 필요 |
| 수도권 생활이동 | 서울 열린데이터광장 **OA-22300**(출발-도착지 기준), 서울시·KT | 월·일 단위 **파일 다운로드**(행정동·시간대·목적=귀가) | 심야 귀가 실제 규모 → 앱 기록 확대 기준, A-02 "수지구→처인구 이동" | 파일 적재 필요 |
| 용인앱택시 운행현황 | data.go.kr **15125676** (OpenAPI, REST/JSON) | data.go.kr 무료 인증키 | 택시 호출 대비 미탑승 → 교통취약 가중치, A-02 "심야 택시 운행" | 키 필요 |

### 용인앱택시 API (확인된 스펙)
- Base URL: `https://apis.data.go.kr/4050000/yonginTaxiStatistics`
- 파라미터: `serviceKey`(필수), `year`, `month`
- 응답: `resultCode`, `resultMsg`, `totalCount`, `items`(읍·면·동별 호출/탑승/도착 건수)
- 미충족 비율 = (호출 − 탑승) ÷ 호출 → 추정 입력(화면엔 운행 건수로 표시)

## 라이브 연동 (구현 완료 — 키만 넣으면 켜짐)

클라이언트는 `domain/demand/publicdata` 에 구현됨. 키는 **환경변수**로 넣고(소스에 하드코딩 금지), 없으면 그 지표만 추정치로 표시한다(파이프라인은 안 막힘).

| 환경변수 | 대상 | 엔드포인트(확인됨) | 상태 |
| --- | --- | --- | --- |
| `YONGIN_TAXI_SERVICE_KEY` | 용인택시 | `GET https://apis.data.go.kr/4050000/yonginTaxiStatistics/driveStatistics?serviceKey=&year=&month=` → items[]{eupMyunDong, requestCount, matchedCount, arrivalCount} | ✅ 실호출 검증(2024-01 처인구 등) |
| `GG_SERVICE_KEY` | 경기 버스 | `GET https://openapi.gg.go.kr/SIGUNBUSANULUSERCNT?KEY=&Type=json&pIndex=&pSize=` → row[]{REGION, TM, ALL_PASNG_ALL_CNT} | ⚠️ 코드 완료. openapi.gg.go.kr 가 **해외/클라우드 IP를 보안 차단**(가짜 키·sample 키·다른 서비스도 동일 차단 → 키 문제 아님). 국내 서버에서 호출해야 검증됨 |

**심야 버스는 CSV 실측 스냅샷으로 이미 실데이터.** openapi.gg.go.kr 라이브가 막혀도, 경기데이터드림 CSV(2025-12, 용인 평일 심야 23~01시 전체통행 = **14,438명/일**)에서 뽑은 **실측값**을 `PublicDemandStatsProvider.NIGHT_BUS_DAILY_SNAPSHOT` 으로 쓴다(지어낸 값 아님). 라이브 키가 국내 서버에서 뚫리면 그 값이 스냅샷을 대체한다.
- 추출: `bus_usage.csv`(경기데이터드림 "시군별 버스 이용객수", euc-kr) → `지역 ~ 용인 & 주말/평일=평일 & 시간코드 ∈ {23,24}` 의 `전체통행 전체` 합(중복 제거). 원본 CSV 는 용량이 커서 커밋하지 않는다(.gitignore).

- `DemandRunGenerator` 가 산출 때 `PublicDemandStatsProvider.fetchFor(연,월)` 로 용인 택시·심야 버스 실측을 받아 `DemandEstimator` 근거 지표에 넣는다. 받으면 "심야 택시 운행: 호출 N·탑승 M (미충족 X%)"·"심야 버스 이용객 N명"이 **실측(OBSERVED)** 으로, 못 받으면 추정치로 표기된다.
- 수도권 생활이동(OA-22300)은 **파일 다운로드**라 아직 추정치. 필요 시 월 1회 적재 배치로 붙인다.
- 실제 모델 공식이 확정되면 `DemandEstimator` 만 교체(집계·클라이언트·조회는 그대로).

### 키 넣는 법 (배포 .env)
```
YONGIN_TAXI_SERVICE_KEY=<data.go.kr 인증키>
GG_SERVICE_KEY=<경기데이터드림 인증키>
```
> data.go.kr 신규 키는 발급 후 1시간쯤 뒤 활성화되는 경우가 있다.

## 출처
- 용인 1101/1101N: https://thewiki.kr/w/용인%20버스%201101 , https://namu.wiki/w/용인시%20버스%20목록
- 경기 시군별 버스 이용객수: https://www.data.go.kr/data/15121882/openapi.do , http://gits.gg.go.kr/gtdb
- 수도권 생활이동: https://data.seoul.go.kr/dataList/OA-22300/F/1/datasetView.do
- 용인앱택시 운행현황: https://www.data.go.kr/data/15125676/openapi.do
