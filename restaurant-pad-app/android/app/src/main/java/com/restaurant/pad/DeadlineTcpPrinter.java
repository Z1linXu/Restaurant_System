package com.restaurant.pad;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

/** One process-wide transport lane; timeout never means permission to retransmit. */
final class DeadlineTcpPrinter {
    interface SocketFactory { Socket create() throws Exception; }
    static final class Result {
        boolean success;
        boolean stopped;
        boolean uncertain;
        String phase = "CONNECT";
        String error = "";
        int bytesWritten;
        long elapsedMs;
    }
    private static final ExecutorService LANE = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "RestaurantNativePrint"); thread.setDaemon(true); return thread;
    });
    private static final AtomicReference<Operation> ACTIVE = new AtomicReference<>();
    private final SocketFactory sockets;
    private final BooleanSupplier owner;
    private volatile Operation owned;
    private volatile Result last = new Result();
    DeadlineTcpPrinter(BooleanSupplier owner) { this(Socket::new, owner); }
    DeadlineTcpPrinter(SocketFactory sockets, BooleanSupplier owner) { this.sockets = sockets; this.owner = owner; }
    static boolean busy() { return ACTIVE.get() != null; }
    Result lastResult() {
        Operation operation = owned;
        if (operation == null || operation.finished.getCount() == 0) return last;
        Result result = new Result(); result.phase = operation.phase; result.bytesWritten = operation.bytesWritten;
        result.elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - operation.startedNanos);
        result.uncertain = operation.aborted; result.error = operation.aborted ? "NATIVE_STOP_UNCONFIRMED" : "";
        return result;
    }
    void close() { Operation operation = owned; if (operation != null) operation.abort(); }

    Result send(String host, int port, int connectTimeout, long deadlineMs, byte[] bytes) {
        Operation operation = new Operation();
        if (!owner.getAsBoolean() || !ACTIVE.compareAndSet(null, operation)) {
            Result result = new Result(); result.error = "NATIVE_EXECUTION_BUSY"; result.uncertain = true; return result;
        }
        owned = operation;
        long start = System.nanoTime();
        LANE.execute(() -> {
            try {
                operation.requireOwner();
                operation.socket = sockets.create();
                operation.requireOwner();
                operation.socket.connect(new InetSocketAddress(host, port), connectTimeout);
                operation.requireOwner();
                OutputStream stream = operation.socket.getOutputStream();
                operation.phase = "WRITE";
                for (int offset = 0; offset < bytes.length;) {
                    operation.requireOwner();
                    int length = Math.min(4096, bytes.length - offset);
                    operation.mayHaveWritten = true; // Set BEFORE write: a thrown write can have sent partial bytes.
                    stream.write(bytes, offset, length);
                    offset += length; operation.bytesWritten = offset;
                }
                operation.phase = "FLUSH";
                operation.requireOwner(); stream.flush();
                operation.success = true; operation.phase = "DONE";
            } catch (Exception ex) {
                operation.error = ex instanceof java.net.SocketTimeoutException ? "TIMEOUT"
                    : ex instanceof java.net.ConnectException ? "CONNECTION_REFUSED"
                    : ex instanceof java.net.NoRouteToHostException || ex instanceof java.net.UnknownHostException ? "UNREACHABLE"
                    : "FLUSH".equals(operation.phase) ? "FLUSH_FAILED"
                    : "WRITE".equals(operation.phase) ? "WRITE_FAILED" : "NATIVE_EXECUTION_STOPPED";
            }
            finally {
                operation.closeSocket();
                operation.finished.countDown();
                ACTIVE.compareAndSet(operation, null);
            }
        });
        boolean timedOut = false;
        boolean finished = false;
        try {
            finished = operation.finished.await(deadlineMs, TimeUnit.MILLISECONDS);
            if (!finished) {
                timedOut = true;
                // Closing can itself be slow: do it outside the caller; retain ACTIVE until actual exit.
                operation.abort();
                finished = operation.finished.await(2000, TimeUnit.MILLISECONDS);
            }
        } catch (InterruptedException ex) { operation.abort(); Thread.currentThread().interrupt(); }
        Result result = new Result();
        result.stopped = finished;
        result.success = finished && operation.success && !timedOut && !operation.aborted;
        result.uncertain = !finished || operation.mayHaveWritten && !result.success;
        result.phase = operation.phase;
        result.bytesWritten = operation.bytesWritten;
        result.elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        result.error = result.success ? "" : timedOut && finished && !operation.mayHaveWritten ? "TIMEOUT"
            : timedOut ? "NATIVE_EXECUTION_TIMEOUT" : operation.error;
        last = result;
        return result;
    }
    private final class Operation {
        final long startedNanos = System.nanoTime();
        final CountDownLatch finished = new CountDownLatch(1);
        volatile Socket socket;
        volatile boolean aborted, mayHaveWritten, success;
        volatile int bytesWritten;
        volatile String phase = "CONNECT", error = "NATIVE_EXECUTION_STOPPED";
        void requireOwner() throws java.io.IOException {
            if (aborted || !owner.getAsBoolean()) throw new java.io.IOException("Native operation owner stopped");
        }
        void abort() {
            aborted = true;
            Thread closer = new Thread(this::closeSocket, "RestaurantNativeClose"); closer.setDaemon(true); closer.start();
        }
        void closeSocket() { Socket current = socket; if (current != null) try { current.close(); } catch (Exception ignored) { } }
    }
}
