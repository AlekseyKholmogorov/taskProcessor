package com.example.tasks.web;

import io.vertx.core.Handler;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebSocketRegistryTest {

    @Mock
    private ServerWebSocket firstSocket;

    @Mock
    private ServerWebSocket secondSocket;

    @Captor
    private ArgumentCaptor<Handler<Void>> firstClose;

    @Captor
    private ArgumentCaptor<Handler<Void>> secondClose;

    @Test
    void sendWritesToRegisteredOpenSocket() {
        WebSocketRegistry registry = new WebSocketRegistry();
        when(firstSocket.closeHandler(any())).thenReturn(firstSocket);
        when(firstSocket.isClosed()).thenReturn(false);

        registry.register(1, firstSocket);
        JsonObject message = new JsonObject().put("progress", 20);
        registry.send(1, message);

        verify(firstSocket).writeTextMessage(message.encode());
    }

    @Test
    void sendIgnoresMissingAndClosedSockets() {
        WebSocketRegistry registry = new WebSocketRegistry();
        when(firstSocket.closeHandler(any())).thenReturn(firstSocket);
        when(firstSocket.isClosed()).thenReturn(true);

        registry.register(1, firstSocket);
        registry.send(1, new JsonObject().put("progress", 20));
        registry.send(99, new JsonObject().put("progress", 40));

        verify(firstSocket, never()).writeTextMessage(anyString());
    }

    @Test
    void closeHandlerRemovesOnlyMatchingSocket() {
        when(firstSocket.closeHandler(firstClose.capture())).thenReturn(firstSocket);
        when(secondSocket.closeHandler(secondClose.capture())).thenReturn(secondSocket);
        WebSocketRegistry registry = new WebSocketRegistry();
        when(firstSocket.closeHandler(firstClose.capture())).thenReturn(firstSocket);
        when(secondSocket.closeHandler(secondClose.capture())).thenReturn(secondSocket);
        when(secondSocket.isClosed()).thenReturn(false);

        registry.register(1, firstSocket);
        registry.register(1, secondSocket);

        firstClose.getValue().handle(null);
        registry.send(1, new JsonObject().put("ok", true));

        verify(secondSocket).writeTextMessage(new JsonObject().put("ok", true).encode());
        verify(firstSocket, never()).writeTextMessage(anyString());
    }
}
