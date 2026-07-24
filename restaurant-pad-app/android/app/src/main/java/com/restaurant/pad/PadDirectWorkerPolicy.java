package com.restaurant.pad;

final class PadDirectWorkerPolicy {
    enum HttpDisposition {
        SUCCESS,
        RECOVERABLE,
        AUTH_STOP,
        CONFLICT,
        BUSINESS_STOP
    }

    enum JobPhase {
        CLAIMING,
        CLAIMED,
        STARTING_PRINT,
        PAYLOAD_FETCHING,
        TCP_CONNECTING,
        TCP_WRITING,
        TCP_FLUSHING,
        LOCAL_PRINT_SUCCEEDED,
        COMPLETING,
        FAIL_REPORTING
    }

    private PadDirectWorkerPolicy() {
    }

    static HttpDisposition classifyHttpStatus(int status) {
        if (status >= 200 && status < 300) {
            return HttpDisposition.SUCCESS;
        }
        if (status == 401 || status == 403) {
            return HttpDisposition.AUTH_STOP;
        }
        if (status == 409) {
            return HttpDisposition.CONFLICT;
        }
        if (status == 408 || status == 429 || status >= 500) {
            return HttpDisposition.RECOVERABLE;
        }
        return HttpDisposition.BUSINESS_STOP;
    }

    static boolean isCurrentGeneration(long callbackGeneration, long currentGeneration) {
        return callbackGeneration > 0 && callbackGeneration == currentGeneration;
    }

    static boolean canRecoverSameJob(JobPhase phase, boolean localPrintMayHaveSucceeded) {
        if (phase == null || localPrintMayHaveSucceeded) {
            return false;
        }
        return phase == JobPhase.CLAIMING
            || phase == JobPhase.CLAIMED
            || phase == JobPhase.STARTING_PRINT
            || phase == JobPhase.PAYLOAD_FETCHING;
    }

    static boolean isAmbiguousOutputPhase(JobPhase phase) {
        return phase == JobPhase.TCP_WRITING
            || phase == JobPhase.TCP_FLUSHING
            || phase == JobPhase.LOCAL_PRINT_SUCCEEDED
            || phase == JobPhase.COMPLETING;
    }
}
