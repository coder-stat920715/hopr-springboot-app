package com.hopr.service;

import com.hopr.config.HoprProperties;
import com.hopr.dto.WalletDtos.ReferralSummary;
import com.hopr.entity.Referral;
import com.hopr.entity.TxnType;
import com.hopr.entity.User;
import com.hopr.exception.BadRequestException;
import com.hopr.repository.ReferralRepository;
import com.hopr.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReferralServiceTest {

    @Mock
    private ReferralRepository referrals;
    @Mock
    private UserRepository users;
    @Mock
    private WalletService walletService;

    private ReferralService service;
    private User referrer;
    private User referee;

    @BeforeEach
    void setUp() {
        service = new ReferralService(referrals, users, walletService, new HoprProperties());
        referrer = User.builder().id(1L).fullName("Referrer").referralCode("HOPRABC123").build();
        referee = User.builder().id(2L).fullName("Referee").referralCode("HOPRXYZ789").build();
    }

    @Test
    void rewardIsTrimmedToRemainingLifetimeCap() {
        when(users.findById(2L)).thenReturn(Optional.of(referee));
        when(users.findByReferralCode("HOPRABC123")).thenReturn(Optional.of(referrer));
        when(referrals.existsByRefereeId(2L)).thenReturn(false);
        Referral earlier = Referral.builder().referrerReward(new BigDecimal("9950")).build();
        when(referrals.findByReferrerId(1L)).thenReturn(List.of(earlier));
        when(referrals.save(any(Referral.class))).thenAnswer(i -> i.getArgument(0));

        service.apply(2L, "hoprabc123");

        verify(walletService).credit(eq(1L), argThat(a -> a.compareTo(new BigDecimal("50")) == 0),
                eq(TxnType.REFERRAL_BONUS), anyString(), any());
        verify(walletService).credit(eq(2L), argThat(a -> a.compareTo(new BigDecimal("50")) == 0),
                eq(TxnType.REFERRAL_BONUS), anyString(), any());
    }

    @Test
    void noReferrerRewardOnceCapReached() {
        when(users.findById(2L)).thenReturn(Optional.of(referee));
        when(users.findByReferralCode("HOPRABC123")).thenReturn(Optional.of(referrer));
        when(referrals.existsByRefereeId(2L)).thenReturn(false);
        when(referrals.findByReferrerId(1L))
                .thenReturn(List.of(Referral.builder().referrerReward(new BigDecimal("10000")).build()));
        when(referrals.save(any(Referral.class))).thenAnswer(i -> i.getArgument(0));

        service.apply(2L, "HOPRABC123");

        verify(walletService, never()).credit(eq(1L), any(), any(), anyString(), any());
    }

    @Test
    void selfReferralIsRejected() {
        when(users.findById(1L)).thenReturn(Optional.of(referrer));
        when(users.findByReferralCode("HOPRABC123")).thenReturn(Optional.of(referrer));
        assertThatThrownBy(() -> service.apply(1L, "HOPRABC123")).isInstanceOf(BadRequestException.class);
    }

    @Test
    void summaryShowsRemainingCap() {
        when(users.findById(1L)).thenReturn(Optional.of(referrer));
        when(referrals.findByReferrerId(1L))
                .thenReturn(List.of(Referral.builder().referrerReward(new BigDecimal("300")).build()));
        ReferralSummary s = service.summary(1L);
        assertThat(s.totalEarned()).isEqualByComparingTo("300");
        assertThat(s.remainingCap()).isEqualByComparingTo("9700");
        assertThat(s.successfulReferrals()).isEqualTo(1);
    }
}
