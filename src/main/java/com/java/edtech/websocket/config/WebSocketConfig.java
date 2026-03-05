package com.java.edtech.websocket.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import com.java.edtech.websocket.handler.RobotWebSocketHandler;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    private final RobotWebSocketHandler robotWebSocketHandler;

    public WebSocketConfig(RobotWebSocketHandler robotWebSocketHandler) {
        this.robotWebSocketHandler = robotWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(robotWebSocketHandler, "/ws/robot").setAllowedOrigins("*");
    }
}
