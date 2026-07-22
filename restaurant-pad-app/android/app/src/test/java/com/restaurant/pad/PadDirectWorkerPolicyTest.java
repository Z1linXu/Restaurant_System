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
}
