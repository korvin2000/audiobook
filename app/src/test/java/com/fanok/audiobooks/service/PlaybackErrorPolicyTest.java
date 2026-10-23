package com.fanok.audiobooks.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.media3.common.PlaybackException;
import com.fanok.audiobooks.service.PlaybackErrorPolicy.Action;
import com.fanok.audiobooks.service.PlaybackErrorPolicy.Decision;
import com.fanok.audiobooks.service.PlaybackErrorPolicy.Kind;
import org.junit.Test;

public class PlaybackErrorPolicyTest {

    private static final int BAD_STATUS = PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS;

    @Test
    public void errorCodes_matchMedia3() {
        assertEquals(PlaybackException.ERROR_CODE_TIMEOUT, PlaybackErrorPolicy.ERROR_CODE_TIMEOUT);
        assertEquals(PlaybackException.ERROR_CODE_IO_UNSPECIFIED, PlaybackErrorPolicy.ERROR_CODE_IO_UNSPECIFIED);
        assertEquals(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                PlaybackErrorPolicy.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED);
        assertEquals(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
                PlaybackErrorPolicy.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT);
        assertEquals(PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE,
                PlaybackErrorPolicy.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE);
        assertEquals(BAD_STATUS, PlaybackErrorPolicy.ERROR_CODE_IO_BAD_HTTP_STATUS);
    }

    @Test
    public void classify_networkTroubleIsTransient() {
        assertEquals(Kind.TRANSIENT, PlaybackErrorPolicy.classify(BAD_STATUS, 503));
        assertEquals(Kind.TRANSIENT, PlaybackErrorPolicy.classify(BAD_STATUS, 500));
        assertEquals(Kind.TRANSIENT,
                PlaybackErrorPolicy.classify(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED, -1));
        assertEquals(Kind.TRANSIENT,
                PlaybackErrorPolicy.classify(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT, -1));
        assertEquals(Kind.TRANSIENT, PlaybackErrorPolicy.classify(PlaybackException.ERROR_CODE_TIMEOUT, -1));
    }

    @Test
    public void classify_refusedAddressIsAuth_missingFileIsOther() {
        assertEquals(Kind.AUTH, PlaybackErrorPolicy.classify(BAD_STATUS, 401));
        assertEquals(Kind.AUTH, PlaybackErrorPolicy.classify(BAD_STATUS, 403));
        assertEquals(Kind.AUTH, PlaybackErrorPolicy.classify(BAD_STATUS, 410));
        assertEquals(Kind.OTHER, PlaybackErrorPolicy.classify(BAD_STATUS, 404));
        assertEquals(Kind.OTHER, PlaybackErrorPolicy.classify(PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND, -1));
    }

    @Test
    public void transient_twoRetriesWithBackoffThenGiveUp() {
        PlaybackErrorPolicy policy = new PlaybackErrorPolicy();
        Decision first = policy.onError(BAD_STATUS, 503);
        assertEquals(Action.RETRY, first.action);
        assertEquals(2_000, first.delayMs);
        Decision second = policy.onError(BAD_STATUS, 503);
        assertEquals(Action.RETRY, second.action);
        assertEquals(5_000, second.delayMs);
        Decision third = policy.onError(BAD_STATUS, 503);
        assertEquals(Action.FAIL, third.action);
        assertEquals(Kind.TRANSIENT, third.kind);

        //Play after the error retries with a full budget
        policy.reset();
        assertEquals(Action.RETRY, policy.onError(BAD_STATUS, 503).action);
    }

    @Test
    public void auth_refreshesTheChapterListOnceThenGivesUp() {
        PlaybackErrorPolicy policy = new PlaybackErrorPolicy();
        assertEquals(Action.REFRESH, policy.onError(BAD_STATUS, 403).action);
        Decision again = policy.onError(BAD_STATUS, 403);
        assertEquals(Action.FAIL, again.action);
        assertEquals(Kind.AUTH, again.kind);
    }

    @Test
    public void decoderAndParserErrors_failAtOnceAsSkippableChapter() {
        PlaybackErrorPolicy policy = new PlaybackErrorPolicy();
        for (int code : new int[]{PlaybackException.ERROR_CODE_DECODING_FAILED,
                PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
                PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
                PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE}) {
            Decision decision = policy.onError(code, -1);
            assertEquals(Action.FAIL, decision.action);
            assertEquals(Kind.CONTENT, decision.kind);
        }
    }

    @Test
    public void clip_endMustFollowStart() {
        assertTrue(PlaybackErrorPolicy.isValidClip(-1, -1));
        assertTrue(PlaybackErrorPolicy.isValidClip(10, -1));
        assertTrue(PlaybackErrorPolicy.isValidClip(10, 20));
        assertFalse(PlaybackErrorPolicy.isValidClip(20, 20));
        assertFalse(PlaybackErrorPolicy.isValidClip(30, 20));
    }
}
