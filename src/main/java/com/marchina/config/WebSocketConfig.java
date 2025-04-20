package com.marchina.config;

import com.marchina.controller.SpeechWebSocketController;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    
    private final SpeechWebSocketController speechWebSocketController;
    
    public WebSocketConfig(SpeechWebSocketController speechWebSocketController) {
        this.speechWebSocketController = speechWebSocketController;
    }
    
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(speechWebSocketController, "/api/speech/stream")
                .setAllowedOrigins("*"); // For development; restrict in production
    }
}
