package com.restaurant.pad;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class PadDirectWorkerPolicyTest {
    @Test
    public void staleGenerationIsRejected() {
        assertTrue(PadDirectWorkerPolicy.isCurrentGeneration(4, 4));
        assertFalse(PadDirectWorkerPolicy.isCurrentGeneration(3, 4));
        assertFalse(PadDirectWorkerPolicy.isCurrentGeneration(0, 4));
    }

    @Test
    public void transientHttpStatusesRecoverButBusinessStatusesStop() {
        assertEquals(PadDirectWorkerPolicy.HttpDisposition.RECOVERABLE, PadDirectWorkerPolicy.classifyHttpStatus(408));
        assertEquals(PadDirectWorkerPolicy.HttpDisposition.RECOVERABLE, PadDirectWorkerPolicy.classifyHttpStatus(429));
        assertEquals(PadDirectWorkerPolicy.HttpDisposition.RECOVERABLE, PadDirectWorkerPolicy.classifyHttpStatus(503));
        assertEquals(PadDirectWorkerPolicy.HttpDisposition.AUTH_STOP, PadDirectWorkerPolicy.classifyHttpStatus(401));
        assertEquals(PadDirectWorkerPolicy.HttpDisposition.CONFLICT, PadDirectWorkerPolicy.classifyHttpStatus(409));
        assertEquals(PadDirectWorkerPolicy.HttpDisposition.BUSINESS_STOP, PadDirectWorkerPolicy.classifyHttpStatus(400));
        assertEquals(PadDirectWorkerPolicy.HttpDisposition.BUSINESS_STOP, PadDirectWorkerPolicy.classifyHttpStatus(404));
    }

    @Test
    public void claimedNetworkRecoveryStaysOnSameJobBeforeTcpWrite() {
        assertTrue(PadDirectWorkerPolicy.canRecoverSameJob(PadDirectWorkerPolicy.JobPhase.CLAIMING, false));
        assertTrue(PadDirectWorkerPolicy.canRecoverSameJob(PadDirectWorkerPolicy.JobPhase.STARTING_PRINT, false));
        assertTrue(PadDirectWorkerPolicy.canRecoverSameJob(PadDirectWorkerPolicy.JobPhase.PAYLOAD_FETCHING, false));
        assertFalse(PadDirectWorkerPolicy.canRecoverSameJob(PadDirectWorkerPolicy.JobPhase.TCP_WRITING, false));
        assertFalse(PadDirectWorkerPolicy.canRecoverSameJob(PadDirectWorkerPolicy.JobPhase.COMPLETING, true));
    }

    @Test
    public void outputAmbiguityRequiresOperatorReview() {
        assertTrue(PadDirectWorkerPolicy.isAmbiguousOutputPhase(PadDirectWorkerPolicy.JobPhase.TCP_WRITING));
        assertTrue(PadDirectWorkerPolicy.isAmbiguousOutputPhase(PadDirectWorkerPolicy.JobPhase.LOCAL_PRINT_SUCCEEDED));
        assertFalse(PadDirectWorkerPolicy.isAmbiguousOutputPhase(PadDirectWorkerPolicy.JobPhase.CLAIMED));
    }

    @Test
    public void lifecyclePauseDefersStopWhileWorkerJobIsInFlight() {
        assertFalse(PadDirectWorkerPolicy.shouldDeferLifecycleStop(true, false, false, false));
        assertTrue(PadDirectWorkerPolicy.shouldDeferLifecycleStop(true, false, true, false));
        assertTrue(PadDirectWorkerPolicy.shouldDeferLifecycleStop(false, false, false, true));
        assertFalse(PadDirectWorkerPolicy.shouldDeferLifecycleStop(true, true, true, false));
        assertFalse(PadDirectWorkerPolicy.shouldDeferLifecycleStop(true, false, false, false));
    }

    @Test
    public void stoppedGenerationCannotStartPolledJob() {
        assertTrue(PadDirectWorkerPolicy.canStartPolledJob(true, true, false, 4, 4));
        assertFalse(PadDirectWorkerPolicy.canStartPolledJob(false, true, false, 4, 4));
        assertFalse(PadDirectWorkerPolicy.canStartPolledJob(true, false, false, 4, 4));
        assertFalse(PadDirectWorkerPolicy.canStartPolledJob(true, true, true, 4, 4));
        assertFalse(PadDirectWorkerPolicy.canStartPolledJob(true, true, false, 4, 5));
    }

    @Test
    public void lifecycleStopAndPolledJobBeginAreAtomicAlternatives() {
        Object lifecycleLock = new Object();

        MutableWorkerState stopWins = new MutableWorkerState();
        synchronized (lifecycleLock) {
            applyLifecycleStop(stopWins);
        }
        synchronized (lifecycleLock) {
            tryBeginPolledJob(stopWins);
        }
        assertFalse(stopWins.activeJob);
        assertTrue(stopWins.stopRequested);
        assertFalse(stopWins.deferredStop);

        MutableWorkerState beginWins = new MutableWorkerState();
        synchronized (lifecycleLock) {
            tryBeginPolledJob(beginWins);
        }
        synchronized (lifecycleLock) {
            applyLifecycleStop(beginWins);
        }
        assertTrue(beginWins.activeJob);
        assertFalse(beginWins.stopRequested);
        assertTrue(beginWins.deferredStop);
    }

    private static void tryBeginPolledJob(MutableWorkerState state) {
        if (PadDirectWorkerPolicy.canStartPolledJob(
            state.appForeground,
            state.workerRunning,
            state.stopRequested,
            state.callbackGeneration,
            state.currentGeneration
        )) {
            state.activeJob = true;
        }
    }

    private static void applyLifecycleStop(MutableWorkerState state) {
        if (PadDirectWorkerPolicy.shouldDeferLifecycleStop(
            state.workerRunning,
            state.stopRequested,
            state.activeJob,
            state.manualJob
        )) {
            state.deferredStop = true;
            return;
        }
        state.stopRequested = true;
        state.workerRunning = false;
        state.currentGeneration += 1;
    }

    private static final class MutableWorkerState {
        boolean appForeground = true;
        boolean workerRunning = true;
        boolean stopRequested = false;
        boolean activeJob = false;
        boolean manualJob = false;
        boolean deferredStop = false;
        long callbackGeneration = 4;
        long currentGeneration = 4;
    }
}
