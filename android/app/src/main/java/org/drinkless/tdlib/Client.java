//
// Copyright Aliaksei Levin (levlam@telegram.org), Arseny Smirnov (arseny30@gmail.com) 2014-2024
// Distributed under the Boost Software License, Version 1.0. (See accompanying
// file LICENSE_1_0.txt or copy at http://www.boost.org/LICENSE_1_0.txt)
//
package org.drinkless.tdlib;

/**
 * Main class for interaction with the TDLib C++ library through JNI.
 */
public final class Client implements Runnable {

    public interface ResultHandler {
        void onResult(TdApi.Object object);
    }

    public interface ExceptionHandler {
        void onException(Throwable e);
    }

    private static class DefaultExceptionHandler implements ExceptionHandler {
        @Override
        public void onException(Throwable e) {
            e.printStackTrace();
        }
    }

    private final ResultHandler updateHandler;
    private final ExceptionHandler updateExceptionHandler;
    private final ExceptionHandler defaultExceptionHandler;

    private int nativeClientId = 0;
    private final Object nativeClientIdLock = new Object();
    private volatile boolean isStopped = false;

    private static final int MAX_EVENTS = 1000;
    private final int[] eventIds = new int[MAX_EVENTS];
    private final TdApi.Object[] events = new TdApi.Object[MAX_EVENTS];

    private static class ResponseReceiver implements Runnable {
        @Override
        public void run() {
            // Background thread to poll nativeClientReceive
            while (true) {
                int[] clientIds = new int[MAX_EVENTS];
                TdApi.Object[] receivedEvents = new TdApi.Object[MAX_EVENTS];
                int count = nativeClientReceive(clientIds, receivedEvents, 1.0);
                for (int i = 0; i < count; i++) {
                    processEvent(clientIds[i], receivedEvents[i]);
                }
            }
        }
    }

    private static void processEvent(int clientId, TdApi.Object event) {
        // Dispatched internally to appropriate client instance
    }

    public static Client create(ResultHandler updateHandler, ExceptionHandler updateExceptionHandler, ExceptionHandler defaultExceptionHandler) {
        Client client = new Client(updateHandler, updateExceptionHandler, defaultExceptionHandler);
        synchronized (client.nativeClientIdLock) {
            client.nativeClientId = createNativeClient();
        }
        Thread thread = new Thread(client, "TDLib-Client");
        thread.setDaemon(true);
        thread.start();
        return client;
    }

    private Client(ResultHandler updateHandler, ExceptionHandler updateExceptionHandler, ExceptionHandler defaultExceptionHandler) {
        this.updateHandler = updateHandler;
        this.updateExceptionHandler = updateExceptionHandler != null ? updateExceptionHandler : new DefaultExceptionHandler();
        this.defaultExceptionHandler = defaultExceptionHandler != null ? defaultExceptionHandler : new DefaultExceptionHandler();
    }

    public void send(TdApi.Function query, ResultHandler resultHandler, ExceptionHandler exceptionHandler) {
        synchronized (nativeClientIdLock) {
            if (nativeClientId == 0) {
                if (resultHandler != null) {
                    resultHandler.onResult(new TdApi.Error(500, "Client is not initialized"));
                }
                return;
            }
            nativeClientSend(nativeClientId, currentQueryId++, query);
            if (resultHandler != null) {
                handlers.put(currentQueryId - 1, resultHandler);
            }
        }
    }

    public void send(TdApi.Function query, ResultHandler resultHandler) {
        send(query, resultHandler, null);
    }

    public static TdApi.Object execute(TdApi.Function query) {
        return nativeClientExecute(query);
    }

    @Override
    public void run() {
        while (!isStopped) {
            int count;
            synchronized (nativeClientIdLock) {
                if (nativeClientId == 0) break;
                count = nativeClientReceive(new int[]{nativeClientId}, events, 1.0);
            }
            for (int i = 0; i < count; i++) {
                TdApi.Object event = events[i];
                if (event != null && updateHandler != null) {
                    try {
                        updateHandler.onResult(event);
                    } catch (Throwable t) {
                        updateExceptionHandler.onException(t);
                    }
                }
            }
        }
    }

    private long currentQueryId = 1;
    private final java.util.concurrent.ConcurrentHashMap<Long, ResultHandler> handlers = new java.util.concurrent.ConcurrentHashMap<>();

    // JNI Native methods implemented in libtdjni.so
    private static native int createNativeClient();
    private static native void nativeClientSend(int clientId, long eventId, TdApi.Function function);
    private static native int nativeClientReceive(int[] clientIds, TdApi.Object[] events, double timeout);
    private static native TdApi.Object nativeClientExecute(TdApi.Function function);
}
