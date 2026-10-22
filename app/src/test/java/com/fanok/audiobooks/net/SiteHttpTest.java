package com.fanok.audiobooks.net;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SiteHttpTest {

    @Test
    public void challengeIsRecognisedByHeaderOrTitle() {
        assertTrue(SiteHttp.isChallenge("challenge", "Книга"));
        assertTrue(SiteHttp.isChallenge(null, "Just a moment..."));
        assertTrue(SiteHttp.isChallenge(null, "DDoS-Guard"));
        assertTrue(SiteHttp.isChallenge(null, "Attention Required! | Cloudflare"));
    }

    @Test
    public void ordinaryPagesAreNotChallenges() {
        assertFalse(SiteHttp.isChallenge(null, "Аудиокниги слушать онлайн"));
        assertFalse(SiteHttp.isChallenge(null, null));
        assertFalse(SiteHttp.isChallenge(null, ""));
    }
}
