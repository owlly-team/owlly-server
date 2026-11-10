package com.yongyong.owlly.domain.party.controller;

import com.yongyong.owlly.common.response.ApiResponse;
import com.yongyong.owlly.common.security.SessionPrincipal;
import com.yongyong.owlly.common.web.ETags;
import com.yongyong.owlly.common.web.IdempotencyKey;
import com.yongyong.owlly.domain.party.dto.BoardingRequest;
import com.yongyong.owlly.domain.party.dto.PartyResponse;
import com.yongyong.owlly.domain.party.service.PartyService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 동승 팟 API (매칭 결과 화면 S-05, 채팅방 S-06).
 *
 * <p>로그인만 확인되면 들어오고, 이 팟의 참여자·대표인지는 서비스에서 확인한다(참여자가 아니면 404, 대표 전용은 403).
 * 응답은 모두 같은 Party 이고 ETag 를 붙인다. 확정·이탈·택시 호출은 그 ETag 를 If-Match 로 받아
 * 학생이 보던 화면 이후에 팟이 바뀌었으면 412 로 다시 조회하게 한다.
 */
@RestController
@RequestMapping("/api/v1/parties/{partyId}")
@RequiredArgsConstructor
public class PartyController {

    private final PartyService partyService;

    /** API-22 팟·멤버·대표·다음 행동 조회. */
    @GetMapping
    public ResponseEntity<ApiResponse<PartyResponse>> getParty(
            @AuthenticationPrincipal SessionPrincipal principal,
            @PathVariable @Positive Long partyId) {
        return withETag(partyService.getParty(principal.userId(), partyId, Instant.now()));
    }

    /** API-23 본인 팟 확정 ("이 팟 확정하기"). */
    @PostMapping("/confirm")
    public ResponseEntity<ApiResponse<PartyResponse>> confirm(
            @AuthenticationPrincipal SessionPrincipal principal,
            @PathVariable @Positive Long partyId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("If-Match") String ifMatch) {
        IdempotencyKey.requireUuid(idempotencyKey);
        return withETag(partyService.confirmParty(principal.userId(), partyId, Instant.now(), ifMatch));
    }

    /** API-24 팟 거절·이탈 ("빠지기", 채팅방 "방 나가기"). */
    @PostMapping("/leave")
    public ResponseEntity<ApiResponse<PartyResponse>> leave(
            @AuthenticationPrincipal SessionPrincipal principal,
            @PathVariable @Positive Long partyId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("If-Match") String ifMatch) {
        IdempotencyKey.requireUuid(idempotencyKey);
        return withETag(partyService.leaveParty(principal.userId(), partyId, Instant.now(), ifMatch));
    }

    /** API-25 대표의 외부 택시 호출 확인 ("택시를 불렀어요"). */
    @PostMapping("/taxi-call-confirmation")
    public ResponseEntity<ApiResponse<PartyResponse>> confirmTaxiCall(
            @AuthenticationPrincipal SessionPrincipal principal,
            @PathVariable @Positive Long partyId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("If-Match") String ifMatch) {
        IdempotencyKey.requireUuid(idempotencyKey);
        return withETag(partyService.confirmTaxiCall(principal.userId(), partyId, Instant.now(), ifMatch));
    }

    /** API-26 본인 탑승·미탑승 기록 ("탔어요"·"못 탔어요"). If-Match 는 받지 않는다(api.md). */
    @PostMapping("/boarding")
    public ResponseEntity<ApiResponse<PartyResponse>> recordBoarding(
            @AuthenticationPrincipal SessionPrincipal principal,
            @PathVariable @Positive Long partyId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody BoardingRequest request) {
        IdempotencyKey.requireUuid(idempotencyKey);
        return withETag(partyService.recordBoarding(principal.userId(), partyId, request.status(), Instant.now()));
    }

    private static ResponseEntity<ApiResponse<PartyResponse>> withETag(PartyResponse party) {
        return ResponseEntity.ok().eTag(ETags.of(party)).body(ApiResponse.of(party));
    }
}
