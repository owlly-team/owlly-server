package com.yongyong.owlly.domain.verification;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.yongyong.owlly.common.exception.ErrorCode;
import com.yongyong.owlly.common.exception.OwllyException;
import com.yongyong.owlly.domain.university.entity.University;
import com.yongyong.owlly.domain.university.repository.UniversityDomainRepository;
import com.yongyong.owlly.domain.university.repository.UniversityRepository;
import com.yongyong.owlly.domain.verification.dto.EmailVerificationRequest;
import com.yongyong.owlly.domain.verification.entity.EmailVerification;
import com.yongyong.owlly.domain.verification.entity.VerificationAttemptLimit;
import com.yongyong.owlly.domain.verification.entity.VerificationFailedRequest;
import com.yongyong.owlly.domain.verification.repository.EmailVerificationRepository;
import com.yongyong.owlly.domain.verification.repository.VerificationAttemptLimitRepository;
import com.yongyong.owlly.domain.verification.repository.VerificationFailedRequestRepository;
import com.yongyong.owlly.domain.verification.service.EmailVerificationService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.MailSendException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class EmailVerificationServiceTest {
    EmailVerificationRepository repo = mock(EmailVerificationRepository.class);
    VerificationAttemptLimitRepository limits = mock(VerificationAttemptLimitRepository.class);
    VerificationFailedRequestRepository failedRequests = mock(VerificationFailedRequestRepository.class);
    Map<String, VerificationFailedRequest> failuresByKey = new HashMap<>();
    UniversityRepository universities = mock(UniversityRepository.class);
    UniversityDomainRepository domains = mock(UniversityDomainRepository.class);
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    @SuppressWarnings("unchecked") ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
    JavaMailSender sender = mock(JavaMailSender.class);
    PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    Map<Long, EmailVerification> verificationsById = new HashMap<>();
    EmailVerificationService service = new EmailVerificationService(repo, limits, failedRequests, universities, domains, jdbc, provider, transactions);

    @BeforeEach void setup() {
        ReflectionTestUtils.setField(service, "secret", "01234567890123456789012345678901");
        ReflectionTestUtils.setField(service, "mailFrom", "test@example.org");
        when(provider.getIfAvailable()).thenReturn(sender);
        when(provider.getObject()).thenReturn(sender);
        when(transactions.getTransaction(any())).thenAnswer(invocation -> new SimpleTransactionStatus());
        when(universities.findById(1L)).thenReturn(Optional.of(University.builder().name("한국외국어대학교").active(true).build()));
        when(domains.existsByUniversityIdAndDomainAndActiveTrue(1L, "hufs.ac.kr")).thenReturn(true);
        when(repo.save(any())).thenAnswer(invocation -> {
            EmailVerification v = invocation.getArgument(0);
            ReflectionTestUtils.setField(v, "id", 1L);
            verificationsById.put(v.getId(), v);
            return v;
        });
        when(repo.findForUpdate(anyLong())).thenAnswer(invocation -> Optional.ofNullable(verificationsById.get(invocation.getArgument(0))));
        when(failedRequests.findById(anyString())).thenAnswer(invocation -> Optional.ofNullable(failuresByKey.get(invocation.getArgument(0))));
        when(failedRequests.save(any())).thenAnswer(invocation -> {
            VerificationFailedRequest request = invocation.getArgument(0);
            failuresByKey.put(request.getRequestKey(), request);
            return request;
        });
    }

    @Test void issueNormalizesEmailInvalidatesPreviousCodeAndSendsNewCode() {
        EmailVerification old = EmailVerification.builder().build();
        when(repo.findByEmailAndConsumedAtIsNullAndInvalidatedAtIsNull("student@hufs.ac.kr")).thenReturn(List.of(old));
        var response = service.issue(new EmailVerificationRequest(1L, "Student@Hufs.Ac.Kr"), "key");
        assertThat(old.getInvalidatedAt()).isNotNull();
        assertThat(response.expiresAt()).isEqualTo(response.resendAvailableAt().plusSeconds(540));
        verify(sender).send(any(SimpleMailMessage.class));
        verify(repo).save(argThat(v -> v.getEmail().equals("student@hufs.ac.kr") && v.getCodeHash().length() == 64));
        var order = inOrder(transactions, sender);
        order.verify(transactions).commit(any());
        order.verify(sender).send(any(SimpleMailMessage.class));
        order.verify(transactions).commit(any());
    }

    @Test void smtpFailureKeepsCommittedCodeAndDoesNotResendSameRequest() {
        doThrow(new MailSendException("delivery failed")).when(sender).send(any(SimpleMailMessage.class));
        var request = new EmailVerificationRequest(1L, "student@hufs.ac.kr");
        assertThatThrownBy(() -> service.issue(request, "issue-key"))
                .isInstanceOfSatisfying(OwllyException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.EMAIL_DELIVERY_UNAVAILABLE));
        EmailVerification saved = verificationsById.get(1L);
        assertThat(saved.getDeliveryStatus()).isEqualTo(com.yongyong.owlly.domain.verification.entity.DeliveryStatus.UNKNOWN);
        when(repo.findByIssueKey("issue-key")).thenReturn(Optional.of(saved));
        assertThatThrownBy(() -> service.issue(request, "issue-key"))
                .isInstanceOfSatisfying(OwllyException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.EMAIL_DELIVERY_UNAVAILABLE));
        verify(sender, times(1)).send(any(SimpleMailMessage.class));
    }

    @Test void dailyLimitStopsDelivery() {
        when(repo.countByEmailAndSentAtGreaterThanEqualAndSentAtLessThan(eq("student@hufs.ac.kr"), any(), any())).thenReturn(10L);
        assertThatThrownBy(() -> service.issue(new EmailVerificationRequest(1L, "student@hufs.ac.kr"), "key"))
                .isInstanceOfSatisfying(OwllyException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.RATE_LIMITED));
        verify(sender, never()).send(any(SimpleMailMessage.class));
    }

    @Test void unsupportedUniversityCannotIssueCodeEvenIfDomainIsActive() {
        when(universities.findById(1L)).thenReturn(Optional.of(University.builder().name("다른 대학교").active(true).build()));
        assertThatThrownBy(() -> service.issue(new EmailVerificationRequest(1L, "student@hufs.ac.kr"), "key"))
                .isInstanceOfSatisfying(OwllyException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.EMAIL_DOMAIN_NOT_ALLOWED));
        verify(sender, never()).send(any(SimpleMailMessage.class));
    }

    @Test void universityRejectsDomainBelongingToAnotherUniversity() {
        when(domains.existsByUniversityIdAndDomainAndActiveTrue(1L, "khu.ac.kr")).thenReturn(true);
        assertThatThrownBy(() -> service.issue(new EmailVerificationRequest(1L, "student@khu.ac.kr"), "key"))
                .isInstanceOfSatisfying(OwllyException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.EMAIL_DOMAIN_NOT_ALLOWED));
        verify(sender, never()).send(any(SimpleMailMessage.class));
    }

    @Test void resendWithinOneMinuteIsBlocked() {
        when(repo.findFirstByEmailOrderBySentAtDescIdDesc(anyString()))
                .thenReturn(Optional.of(EmailVerification.builder().sentAt(Instant.now()).build()));
        assertThatThrownBy(() -> service.issue(new EmailVerificationRequest(1L, "student@hufs.ac.kr"), "key"))
                .isInstanceOfSatisfying(OwllyException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.RATE_LIMITED));
        verify(sender, never()).send(any(SimpleMailMessage.class));
    }

    @Test void fifthIncorrectCodeBlocksVerificationForTenMinutesAcrossResends() {
        String email = "student@hufs.ac.kr";
        EmailVerification v = EmailVerification.builder().email(email).codeHash("wrong-hash")
                .expiresAt(Instant.now().plusSeconds(600)).build();
        VerificationAttemptLimit limit = new VerificationAttemptLimit(email);
        when(repo.findEmailById(1L)).thenReturn(Optional.of(email));
        when(repo.findForUpdate(1L)).thenReturn(Optional.of(v));
        when(limits.findById(email)).thenReturn(Optional.of(limit));
        for (int i = 0; i < 4; i++) {
            String key = "key-" + i;
            assertThatThrownBy(() -> service.verify(1L, "123456", key))
                    .isInstanceOfSatisfying(OwllyException.class,
                            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_VERIFICATION_CODE));
        }
        assertThatThrownBy(() -> service.verify(1L, "123456", "key-4"))
                .isInstanceOfSatisfying(OwllyException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.RATE_LIMITED));
        assertThat(limit.getBlockedUntil()).isAfter(Instant.now().plusSeconds(590));
        assertThatThrownBy(() -> service.verify(1L, "123456", "key-4"))
                .isInstanceOfSatisfying(OwllyException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.RATE_LIMITED));
        assertThat(limit.getFailures()).isEqualTo(5);
    }

    @Test void retryingFailedRequestDoesNotCountAgainAndDifferentPayloadConflicts() {
        String email = "student@hufs.ac.kr";
        EmailVerification v = EmailVerification.builder().email(email).codeHash("wrong-hash")
                .expiresAt(Instant.now().plusSeconds(600)).build();
        VerificationAttemptLimit limit = new VerificationAttemptLimit(email);
        when(repo.findEmailById(1L)).thenReturn(Optional.of(email));
        when(repo.findForUpdate(1L)).thenReturn(Optional.of(v));
        when(limits.findById(email)).thenReturn(Optional.of(limit));

        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> service.verify(1L, "123456", "same-key"))
                    .isInstanceOfSatisfying(OwllyException.class,
                            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_VERIFICATION_CODE));
        }
        assertThat(limit.getFailures()).isEqualTo(1);
        assertThat(v.getFailedAttempts()).isEqualTo(1);
        assertThatThrownBy(() -> service.verify(1L, "654321", "same-key"))
                .isInstanceOfSatisfying(OwllyException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.IDEMPOTENCY_CONFLICT));
        assertThatThrownBy(() -> service.verify(2L, "123456", "same-key"))
                .isInstanceOfSatisfying(OwllyException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.IDEMPOTENCY_CONFLICT));
    }

    @Test void validCodeProducesSingleUseGrantAndSameKeyReplaysIt() {
        service.issue(new EmailVerificationRequest(1L, "student@hufs.ac.kr"), "issue-key");
        ArgumentCaptor<EmailVerification> saved = ArgumentCaptor.forClass(EmailVerification.class);
        verify(repo).save(saved.capture());
        EmailVerification v = saved.getValue();
        ReflectionTestUtils.setField(v, "id", 1L);
        ArgumentCaptor<SimpleMailMessage> mail = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(mail.capture());
        String code = java.util.regex.Pattern.compile("[0-9]{6}").matcher(mail.getValue().getText())
                .results().findFirst().orElseThrow().group();
        when(repo.findEmailById(1L)).thenReturn(Optional.of(v.getEmail()));
        when(repo.findForUpdate(1L)).thenReturn(Optional.of(v));
        var grant = service.verify(1L, code, "verify-key");
        assertThat(grant.verificationToken()).hasSize(64);
        assertThat(v.getGrantHash()).isNotEqualTo(grant.verificationToken());
        assertThat(v.getConsumedAt()).isNotNull();
        when(repo.findByVerifyKey("verify-key")).thenReturn(Optional.of(v));
        assertThat(service.verify(1L, code, "verify-key")).isEqualTo(grant);
    }
}
