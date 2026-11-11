package com.saasbasics.platform.modules.ai.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class AiRunStateMachineTest {

    @Test
    void allowsExpectedM3LifecycleTransitions() {
        RunState s = RunState.CREATED;
        s = s.transitionTo(RunState.RUNNING);
        s = s.transitionTo(RunState.WAITING_APPROVAL);
        s = s.transitionTo(RunState.RUNNING);
        s = s.transitionTo(RunState.SUCCEEDED);

        assertThat(s).isEqualTo(RunState.SUCCEEDED);
        assertThat(s.isTerminal()).isTrue();
    }

    @Test
    void allowsHumanRejectionFromWaitingApproval() {
        RunState s = RunState.CREATED.transitionTo(RunState.RUNNING)
                .transitionTo(RunState.WAITING_APPROVAL)
                .transitionTo(RunState.REJECTED);

        assertThat(s).isEqualTo(RunState.REJECTED);
        assertThat(s.isTerminal()).isTrue();
    }

    @Test
    void rejectsIllegalStateTransitionsAndTerminalMutations() {
        assertThatThrownBy(() -> RunState.CREATED.transitionTo(RunState.SUCCEEDED))
                .isInstanceOf(AiDomainException.class)
                .extracting(e -> ((AiDomainException) e).getCode())
                .isEqualTo("AI_RUN_STATE_TRANSITION_INVALID");

        assertThatThrownBy(() -> RunState.SUCCEEDED.transitionTo(RunState.RUNNING))
                .isInstanceOf(AiDomainException.class);

        assertThatThrownBy(() -> RunState.REJECTED.transitionTo(RunState.RUNNING))
                .isInstanceOf(AiDomainException.class);
    }
}
