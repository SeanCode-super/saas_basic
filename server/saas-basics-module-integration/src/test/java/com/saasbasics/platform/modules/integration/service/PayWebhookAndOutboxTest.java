package com.saasbasics.platform.modules.integration.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.modules.integration.mapper.PayOrderMapper;
import com.saasbasics.platform.modules.integration.mapper.PayRefundMapper;
import com.saasbasics.platform.outbox.PlatformOutboxService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;

class PayWebhookAndOutboxTest {

    @Test
    void verifiesHmacWebhookSignatureAndBlocksMockPayInProd() {
        MockEnvironment devEnv = new MockEnvironment();
        devEnv.setActiveProfiles("dev");
        PayChannelVerifier verifier = new PayChannelVerifier(devEnv);

        String payload = "{\"orderNo\":\"PO_1001\",\"channelTradeNo\":\"WX_999\"}";
        String ts = "1762870000";
        String sig = PayChannelVerifier.hmacSha256Hex("saas-dev-pay-webhook-secret", ts + "." + payload);
        verifier.verifyOrThrow("WECHAT", payload, sig, ts);

        assertThatThrownBy(() -> verifier.verifyOrThrow("WECHAT", payload, "tampered-sig", ts))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo("PAY_NOTIFY_SIGN_INVALID");

        MockEnvironment prodEnv = new MockEnvironment();
        prodEnv.setActiveProfiles("prod");
        StaticListableBeanFactory bf = new StaticListableBeanFactory();
        bf.addBean("env", prodEnv);
        bf.addBean("verifier", new PayChannelVerifier(prodEnv));

        PayCenterService prodService = new PayCenterService(
                bf.getBeanProvider(PayOrderMapper.class),
                bf.getBeanProvider(PayRefundMapper.class),
                bf.getBeanProvider(PlatformOutboxService.class),
                bf.getBeanProvider(PayChannelVerifier.class),
                bf.getBeanProvider(Environment.class)
        );

        assertThatThrownBy(() -> prodService.mockPaySuccess("PO_1001"))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo("MOCK_PAY_DISABLED_IN_PROD");
    }
}
