package com.yongyong.owlly.domain.auth.service;

import com.yongyong.owlly.common.exception.ErrorCode;
import com.yongyong.owlly.common.exception.OwllyException;
import com.yongyong.owlly.domain.region.entity.AdministrativeArea;
import com.yongyong.owlly.domain.region.entity.AreaLevel;
import com.yongyong.owlly.domain.region.service.DropoffLocationService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/** 공통 서비스가 제공되면 자동 주입되는 가입 측 연결 지점. 외부 지도 호출은 구현하지 않는다. */
@Component
@RequiredArgsConstructor
public class RegistrationDropoffLocation {
    private final ObjectProvider<DropoffLocationService> resolvers;

    public AdministrativeArea resolveDong(double latitude, double longitude) {
        var resolver = resolvers.getIfAvailable();
        if (resolver == null) throw new OwllyException(ErrorCode.POLICY_NOT_CONFIGURED);
        var dong = resolver.resolveDong(latitude, longitude);
        if (dong == null || dong.getId() == null || dong.getLevel() != AreaLevel.DONG) {
            throw new OwllyException(ErrorCode.LOCATION_UNRESOLVABLE);
        }
        return dong;
    }
}
