package com.saasbasics.platform.modules.ai.domain;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * AI Run 执行生命周期状态机：
 * CREATED -> RUNNING -> WAITING_APPROVAL -> RUNNING -> SUCCEEDED | REJECTED
 * RUNNING / WAITING_APPROVAL -> FAILED | CANCELLED | TIMED_OUT
 */
public enum RunState {
    CREATED,
    RUNNING,
    WAITING_APPROVAL,
    REJECTED,
    SUCCEEDED,
    FAILED,
    CANCELLED,
    TIMED_OUT;

    private static final Map<RunState, Set<RunState>> TRANSITIONS = new EnumMap<>(RunState.class);

    static {
        TRANSITIONS.put(CREATED, EnumSet.of(RUNNING, CANCELLED, FAILED));
        TRANSITIONS.put(RUNNING, EnumSet.of(WAITING_APPROVAL, SUCCEEDED, FAILED, CANCELLED, TIMED_OUT));
        TRANSITIONS.put(WAITING_APPROVAL, EnumSet.of(RUNNING, REJECTED, CANCELLED, TIMED_OUT, FAILED));
        TRANSITIONS.put(REJECTED, EnumSet.noneOf(RunState.class));
        TRANSITIONS.put(SUCCEEDED, EnumSet.noneOf(RunState.class));
        TRANSITIONS.put(FAILED, EnumSet.noneOf(RunState.class));
        TRANSITIONS.put(CANCELLED, EnumSet.noneOf(RunState.class));
        TRANSITIONS.put(TIMED_OUT, EnumSet.noneOf(RunState.class));
    }

    public boolean canTransitionTo(RunState next) {
        return next != null && TRANSITIONS.getOrDefault(this, EnumSet.noneOf(RunState.class)).contains(next);
    }

    public RunState transitionTo(RunState next) {
        if (!canTransitionTo(next)) {
            throw new AiDomainException(
                    "AI_RUN_STATE_TRANSITION_INVALID",
                    "非法 Run 状态流转: " + this.name() + " -> " + (next == null ? "null" : next.name())
            );
        }
        return next;
    }

    public boolean isTerminal() {
        return TRANSITIONS.getOrDefault(this, EnumSet.noneOf(RunState.class)).isEmpty();
    }
}
