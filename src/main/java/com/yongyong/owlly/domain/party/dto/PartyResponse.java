package com.yongyong.owlly.domain.party.dto;

import com.yongyong.owlly.domain.party.entity.PartyStatus;
import com.yongyong.owlly.domain.region.dto.PickupPointResponse;
import java.time.Instant;
import java.util.List;

/**
 * 팟·멤버·대표·다음 행동 (docs/api.md 5절 Party).
 *
 * <p>매칭 알림이나 매칭 대기 카드에서 들어오는 매칭 결과 화면(S-05)이 이 값으로 그려진다.
 * allowedActions 로 "이 팟 확정하기"·"빠지기" 버튼을 켤지 정한다.
 * ID 는 Long 이지만 JacksonConfig 에 의해 JSON 에서는 문자열로 나간다.
 */
public record PartyResponse(
        Long id,                             // 팟 번호
        PartyStatus status,                  // FORMING / CONFIRMING / PREPAYING / READY / RIDING / SETTLING / COMPLETED / CANCELLED
        PickupPointResponse pickupPoint,     // 집결 거점 (이름·공개 좌표·집결 안내)
        Instant departureAt,                 // 출발 시각
        Instant confirmDeadlineAt,           // 확정 마감. 홈 카드·매칭 결과의 "22:49까지 확정해 주세요"와 남은 시간
        Long representativeMemberId,         // 대표의 팟 참여 번호. 정해지기 전이면 null
        Long myMemberId,                     // 이 화면을 보는 학생의 팟 참여 번호
        List<PartyMemberResponse> members,   // 참여자 목록 (팟에 들어온 순서)
        int routeVersion,                    // 지금 하차 순서의 버전. 인원이 바뀌면 올라간다
        Instant taxiCalledAt,                // 대표가 "택시를 불렀어요"를 누른 시각. 아직이면 null
        Long roomId,                         // 들어갈 수 있는 채팅방. 확정 전이거나 방이 없으면 null
        List<PartyAction> allowedActions     // 지금 누를 수 있는 버튼
) {}
