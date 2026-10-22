package com.fanok.audiobooks.service;

import androidx.annotation.NonNull;

/**
 * What the player does after a playback error, kept free of Android types so it is unit-testable.
 * One instance tracks the retry budget of the chapter being played; {@link #reset()} restores it once the chapter
 * plays again or another chapter is loaded.
 */
public final class PlaybackErrorPolicy {

    /** Delays of the re-prepare attempts after a transient error; their count is the retry budget. */
    static final long[] BACKOFF_MS = {2_000, 5_000};

    // androidx.media3.common.PlaybackException error codes
    static final int ERROR_CODE_TIMEOUT = 1003;
    static final int ERROR_CODE_IO_UNSPECIFIED = 2000;
    static final int ERROR_CODE_IO_NETWORK_CONNECTION_FAILED = 2001;
    static final int ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT = 2002;
    static final int ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE = 2003;
    static final int ERROR_CODE_IO_BAD_HTTP_STATUS = 2004;

    public enum Kind {
        /** Network trouble that may pass: HTTP 5xx, timeouts, failed connections. */
        TRANSIENT,
        /** The address expired or is refused (HTTP 401/403/410): a fresh chapter list may fix it. */
        AUTH,
        /** The chapter itself cannot be played (parsing, decoding, unsupported format): Next skips it. */
        CONTENT,
        /** Anything else (missing file, 404, no permission ...): reported, Play retries. */
        OTHER
    }

    public enum Action {
        /** Re-prepare the chapter at the checkpoint position after {@link Decision#delayMs}. */
        RETRY,
        /** Refresh the chapter list once in the background, then re-prepare. */
        REFRESH,
        /** Stop trying: publish the error, Play retries and Next skips. */
        FAIL
    }

    public static final class Decision {
        @NonNull
        public final Action action;
        @NonNull
        public final Kind kind;
        public final long delayMs;

        Decision(@NonNull Action action, @NonNull Kind kind, long delayMs) {
            this.action = action;
            this.kind = kind;
            this.delayMs = delayMs;
        }
    }

    private int retries;

    private boolean refreshed;

    /**
     * @param errorCode  {@code PlaybackException.errorCode}
     * @param httpStatus the response code of a bad HTTP status, or -1
     */
    @NonNull
    public static Kind classify(int errorCode, int httpStatus) {
        switch (errorCode) {
            case ERROR_CODE_TIMEOUT:
            case ERROR_CODE_IO_UNSPECIFIED:
            case ERROR_CODE_IO_NETWORK_CONNECTION_FAILED:
            case ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT:
                return Kind.TRANSIENT;
            case ERROR_CODE_IO_BAD_HTTP_STATUS:
                if (httpStatus >= 500 && httpStatus <= 599) {
                    return Kind.TRANSIENT;
                }
                if (httpStatus == 401 || httpStatus == 403 || httpStatus == 410) {
                    return Kind.AUTH;
                }
                return Kind.OTHER;
            case ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE:
                return Kind.CONTENT;
            default:
                // 3xxx parsing, 4xxx decoding
                return errorCode >= 3000 && errorCode < 5000 ? Kind.CONTENT : Kind.OTHER;
        }
    }

    /** Spends the budget for one error of the current chapter and says what to do next. */
    @NonNull
    public Decision onError(int errorCode, int httpStatus) {
        Kind kind = classify(errorCode, httpStatus);
        if (kind == Kind.TRANSIENT && retries < BACKOFF_MS.length) {
            return new Decision(Action.RETRY, kind, BACKOFF_MS[retries++]);
        }
        if (kind == Kind.AUTH && !refreshed) {
            refreshed = true;
            return new Decision(Action.REFRESH, kind, 0);
        }
        return new Decision(Action.FAIL, kind, 0);
    }

    /** The chapter played, or another one is loaded, or the user asked to retry: a full budget again. */
    public void reset() {
        retries = 0;
        refreshed = false;
    }

    /** A clip whose end is not after its start would fail or play nothing; negative values mean "no clipping". */
    public static boolean isValidClip(int startSeconds, int finishSeconds) {
        return startSeconds < 0 || finishSeconds < 0 || finishSeconds > startSeconds;
    }
}
