package com.tlmstatueanimation.client.anim;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * AnimationDecision 状态机测试（D6 dirty-once 语义）。
 */
class AnimationDecisionTest {

    @Test
    void firstSeenPlay() {
        AnimationDecision.Decision d = AnimationDecision.decide("extra1", true, null, false);
        assertEquals(AnimationDecision.Action.PLAY, d.action());
        assertEquals("extra1", d.animKey());
        assertEquals(true, d.playing());
    }

    @Test
    void repeatedTickIsNone() {
        AnimationDecision.Decision first = AnimationDecision.decide("extra1", true, null, false);
        AnimationDecision.Decision second = AnimationDecision.decide("extra1", true, first.animKey(), first.playing());
        assertEquals(AnimationDecision.Action.NONE, second.action());
    }

    @Test
    void switchAnimationWhilePlaying() {
        AnimationDecision.Decision d = AnimationDecision.decide("extra2", true, "extra1", true);
        assertEquals(AnimationDecision.Action.PLAY, d.action());
        assertEquals("extra2", d.animKey());
    }

    @Test
    void stopAfterPlaying() {
        AnimationDecision.Decision d = AnimationDecision.decide("extra1", false, "extra1", true);
        assertEquals(AnimationDecision.Action.STOP, d.action());
        assertEquals(false, d.playing());
    }

    @Test
    void stopAfterStopIsNone() {
        AnimationDecision.Decision d = AnimationDecision.decide("extra1", false, "extra1", false);
        assertEquals(AnimationDecision.Action.NONE, d.action());
    }

    @Test
    void replayAfterStop() {
        AnimationDecision.Decision d = AnimationDecision.decide("extra1", true, "extra1", false);
        assertEquals(AnimationDecision.Action.PLAY, d.action());
    }

    @Test
    void emptyKeyWithPlayingIsIgnored() {
        AnimationDecision.Decision d = AnimationDecision.decide("", true, null, false);
        assertEquals(AnimationDecision.Action.NONE, d.action());
        // 不污染已施加状态
        assertEquals(null, d.animKey());
        assertEquals(false, d.playing());
    }

    @Test
    void emptyKeyPlayingDoesNotBreakLaterPlay() {
        AnimationDecision.Decision ignored = AnimationDecision.decide("", true, null, false);
        AnimationDecision.Decision d = AnimationDecision.decide("extra3", true, ignored.animKey(), ignored.playing());
        assertEquals(AnimationDecision.Action.PLAY, d.action());
        assertEquals("extra3", d.animKey());
    }

    @Test
    void firstSeenStopIsNone() {
        AnimationDecision.Decision d = AnimationDecision.decide("extra1", false, null, false);
        assertEquals(AnimationDecision.Action.NONE, d.action());
    }
}
