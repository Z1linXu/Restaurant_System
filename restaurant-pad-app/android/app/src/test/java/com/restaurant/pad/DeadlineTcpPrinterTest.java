package com.restaurant.pad;

import static org.junit.Assert.*;
import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.Test;

public class DeadlineTcpPrinterTest {
    static class FakeSocket extends Socket {
        final CountDownLatch closed = new CountDownLatch(1);
        final boolean connectStall, writeStall, flushFail;
        FakeSocket(boolean connectStall, boolean writeStall, boolean flushFail) {
            this.connectStall = connectStall; this.writeStall = writeStall; this.flushFail = flushFail;
        }
        void stall() throws IOException {
            try { if (!closed.await(4, TimeUnit.SECONDS)) throw new IOException("test timeout"); }
            catch (InterruptedException e) { throw new IOException(e); }
            throw new IOException("closed");
        }
        @Override public void connect(SocketAddress address, int timeout) throws IOException { if (connectStall) stall(); }
        @Override public OutputStream getOutputStream() {
            return new OutputStream() {
                public void write(int b) throws IOException { if (writeStall) stall(); }
                public void flush() throws IOException { if (flushFail) throw new IOException("flush"); }
            };
        }
        @Override public void close() { closed.countDown(); }
    }
    @Test public void connectDeadlineStopsSocketWithoutPossibleBytes() {
        FakeSocket socket = new FakeSocket(true, false, false);
        DeadlineTcpPrinter.Result result = new DeadlineTcpPrinter(() -> socket, () -> true).send("127.0.0.1", 9100, 500, 20, new byte[]{1});
        assertFalse(result.success); assertTrue(result.stopped); assertFalse(result.uncertain); assertEquals(0, result.bytesWritten);
        assertEquals("TIMEOUT", result.error);
    }
    @Test public void writeTimeoutWithZeroConfirmedBytesIsStillUncertain() {
        FakeSocket socket = new FakeSocket(false, true, false);
        DeadlineTcpPrinter.Result result = new DeadlineTcpPrinter(() -> socket, () -> true).send("127.0.0.1", 9100, 500, 20, new byte[]{1});
        assertFalse(result.success); assertTrue(result.stopped); assertTrue(result.uncertain); assertEquals(0, result.bytesWritten);
    }
    @Test public void flushFailureAfterBytesIsUncertain() {
        DeadlineTcpPrinter.Result result = new DeadlineTcpPrinter(() -> new FakeSocket(false, false, true), () -> true)
            .send("127.0.0.1", 9100, 500, 1000, new byte[]{1, 2});
        assertFalse(result.success); assertTrue(result.stopped); assertTrue(result.uncertain); assertEquals(2, result.bytesWritten);
    }
    @Test public void oldActivityAndNewActivityCannotExecuteInParallel() throws Exception {
        AtomicBoolean oldOwner = new AtomicBoolean(true);
        FakeSocket socket = new FakeSocket(false, true, false);
        DeadlineTcpPrinter old = new DeadlineTcpPrinter(() -> socket, oldOwner::get);
        ExecutorService caller = Executors.newSingleThreadExecutor();
        try {
            Future<DeadlineTcpPrinter.Result> running = caller.submit(() -> old.send("127.0.0.1", 9100, 500, 3000, new byte[]{1}));
            long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
            while (!DeadlineTcpPrinter.busy() && System.nanoTime() < until) Thread.yield();
            DeadlineTcpPrinter newer = new DeadlineTcpPrinter(() -> new FakeSocket(false, false, false), () -> true);
            assertEquals("NATIVE_EXECUTION_BUSY", newer.send("127.0.0.1", 9100, 500, 1000, new byte[]{1}).error);
            oldOwner.set(false); old.close(); assertFalse(running.get(3, TimeUnit.SECONDS).success);
            assertFalse(old.send("127.0.0.1", 9100, 500, 1000, new byte[]{1}).success);
        } finally { old.close(); caller.shutdownNow(); }
    }
    @Test public void completedTransportHasConfirmedExit() {
        DeadlineTcpPrinter.Result result = new DeadlineTcpPrinter(() -> new FakeSocket(false, false, false), () -> true)
            .send("127.0.0.1", 9100, 500, 1000, new byte[]{1});
        assertTrue(result.success); assertTrue(result.stopped); assertFalse(result.uncertain);
    }
    @Test public void refusedConnectionPreservesSafeRetryCodeAndConfirmedStop() {
        FakeSocket socket = new FakeSocket(false, false, false) {
            @Override public void connect(SocketAddress address, int timeout) throws IOException { throw new ConnectException("synthetic"); }
        };
        DeadlineTcpPrinter.Result result = new DeadlineTcpPrinter(() -> socket, () -> true).send("127.0.0.1", 9100, 500, 1000, new byte[]{1});
        assertEquals("CONNECTION_REFUSED", result.error); assertEquals("CONNECT", result.phase);
        assertTrue(result.stopped); assertFalse(result.uncertain); assertEquals(0, result.bytesWritten);
    }
    @Test public void unconfirmedStopKeepsProcessLaneUntilOldOperationReallyExits() throws Exception {
        CountDownLatch unblock = new CountDownLatch(1);
        CountDownLatch writing = new CountDownLatch(1);
        FakeSocket socket = new FakeSocket(false, false, false) {
            @Override public OutputStream getOutputStream() {
                return new OutputStream() {
                    public void write(int b) throws IOException {
                        writing.countDown();
                        try { unblock.await(5, TimeUnit.SECONDS); } catch (InterruptedException e) { throw new IOException(e); }
                        throw new IOException("late termination");
                    }
                };
            }
            @Override public void close() { /* simulate native close not interrupting a blocked write */ }
        };
        DeadlineTcpPrinter printer = new DeadlineTcpPrinter(() -> socket, () -> true);
        try {
            DeadlineTcpPrinter.Result result = printer.send("127.0.0.1", 9100, 500, 40, new byte[]{1});
            assertEquals(0, writing.getCount()); assertFalse(result.stopped); assertTrue(result.uncertain);
            assertTrue(DeadlineTcpPrinter.busy());
            assertEquals("NATIVE_EXECUTION_BUSY", new DeadlineTcpPrinter(() -> true).send("127.0.0.1", 9100, 500, 50, new byte[]{1}).error);
        } finally {
            unblock.countDown();
            long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
            while (DeadlineTcpPrinter.busy() && System.nanoTime() < until) Thread.yield();
            assertFalse(DeadlineTcpPrinter.busy());
        }
    }
}
