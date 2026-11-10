package com.yongyong.owlly.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yongyong.owlly.common.exception.ErrorCode;
import com.yongyong.owlly.common.exception.OwllyException;
import com.yongyong.owlly.domain.auth.service.RegistrationDropoffLocation;
import com.yongyong.owlly.domain.region.entity.AdministrativeArea;
import com.yongyong.owlly.domain.region.entity.AreaLevel;
import com.yongyong.owlly.domain.region.service.DropoffLocationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.test.util.ReflectionTestUtils;

class RegistrationDropoffLocationTest {
    @Test void missingCommonImplementationReturnsPolicyNotConfigured() {
        var factory = new StaticListableBeanFactory();
        var location = new RegistrationDropoffLocation(factory.getBeanProvider(DropoffLocationService.class));
        assertThatThrownBy(() -> location.resolveDong(37.3, 127.1))
                .isInstanceOfSatisfying(OwllyException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.POLICY_NOT_CONFIGURED));
    }

    @Test void connectsProvidedCommonImplementationWithoutChangingRegistration() {
        var dong = AdministrativeArea.builder().level(AreaLevel.DONG).name("테스트동").officialCode("9900000000").build();
        ReflectionTestUtils.setField(dong, "id", 123L);
        DropoffLocationService resolver = (latitude, longitude) -> {
            assertThat(latitude).isEqualTo(37.3);
            assertThat(longitude).isEqualTo(127.1);
            return dong;
        };
        var factory = new StaticListableBeanFactory();
        factory.addBean("commonDropoffResolver", resolver);
        var location = new RegistrationDropoffLocation(factory.getBeanProvider(DropoffLocationService.class));
        assertThat(location.resolveDong(37.3, 127.1).getId()).isEqualTo(123L);
    }

    @Test void cannotPersistAnUnresolvedOrNonDongResult() {
        var factory = new StaticListableBeanFactory();
        factory.addBean("commonDropoffResolver", (DropoffLocationService) (lat, lng) ->
                AdministrativeArea.builder().level(AreaLevel.DISTRICT).name("잘못된 구").build());
        var location = new RegistrationDropoffLocation(factory.getBeanProvider(DropoffLocationService.class));
        assertThatThrownBy(() -> location.resolveDong(37.3, 127.1))
                .isInstanceOfSatisfying(OwllyException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.LOCATION_UNRESOLVABLE));
    }
}
