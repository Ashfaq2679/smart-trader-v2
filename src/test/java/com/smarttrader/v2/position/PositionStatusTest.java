package com.smarttrader.v2.position;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PositionStatusTest {

    @Test
    void pendingCanTransitionToPartiallyFilledOpenOrClosed() {
        assertThat(PositionStatus.PENDING.canTransitionTo(PositionStatus.PARTIALLY_FILLED)).isTrue();
        assertThat(PositionStatus.PENDING.canTransitionTo(PositionStatus.OPEN)).isTrue();
        assertThat(PositionStatus.PENDING.canTransitionTo(PositionStatus.CLOSED)).isTrue();
    }

    @Test
    void pendingCannotTransitionToItself() {
        assertThat(PositionStatus.PENDING.canTransitionTo(PositionStatus.PENDING)).isFalse();
    }

    @Test
    void partiallyFilledCanAccumulateMoreFillsOrCompleteOrClose() {
        assertThat(PositionStatus.PARTIALLY_FILLED.canTransitionTo(PositionStatus.PARTIALLY_FILLED)).isTrue();
        assertThat(PositionStatus.PARTIALLY_FILLED.canTransitionTo(PositionStatus.OPEN)).isTrue();
        assertThat(PositionStatus.PARTIALLY_FILLED.canTransitionTo(PositionStatus.CLOSED)).isTrue();
    }

    @Test
    void openCanOnlyTransitionToClosed() {
        assertThat(PositionStatus.OPEN.canTransitionTo(PositionStatus.CLOSED)).isTrue();
        assertThat(PositionStatus.OPEN.canTransitionTo(PositionStatus.OPEN)).isFalse();
        assertThat(PositionStatus.OPEN.canTransitionTo(PositionStatus.PARTIALLY_FILLED)).isFalse();
        assertThat(PositionStatus.OPEN.canTransitionTo(PositionStatus.PENDING)).isFalse();
    }

    @Test
    void closedIsTerminal() {
        for (PositionStatus target : PositionStatus.values()) {
            assertThat(PositionStatus.CLOSED.canTransitionTo(target)).isFalse();
        }
    }
}
