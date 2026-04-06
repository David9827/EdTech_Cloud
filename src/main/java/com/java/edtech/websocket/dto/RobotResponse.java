package com.java.edtech.websocket.dto;

public class RobotResponse {
    private RobotEventType type;
    private String sessionId;
    private String utteranceId;
    private String cancelledUtteranceId;
    private String text;
    private String errorCode;
    private String errorMessage;
    private String audioMimeType;
    private Integer sampleRate;
    private Integer channels;
    private Integer audioBytesLength;

    public RobotEventType getType() {
        return type;
    }

    public void setType(RobotEventType type) {
        this.type = type;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getUtteranceId() {
        return utteranceId;
    }

    public void setUtteranceId(String utteranceId) {
        this.utteranceId = utteranceId;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getCancelledUtteranceId() {
        return cancelledUtteranceId;
    }

    public void setCancelledUtteranceId(String cancelledUtteranceId) {
        this.cancelledUtteranceId = cancelledUtteranceId;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getAudioMimeType() {
        return audioMimeType;
    }

    public void setAudioMimeType(String audioMimeType) {
        this.audioMimeType = audioMimeType;
    }

    public Integer getSampleRate() {
        return sampleRate;
    }

    public void setSampleRate(Integer sampleRate) {
        this.sampleRate = sampleRate;
    }

    public Integer getChannels() {
        return channels;
    }

    public void setChannels(Integer channels) {
        this.channels = channels;
    }

    public Integer getAudioBytesLength() {
        return audioBytesLength;
    }

    public void setAudioBytesLength(Integer audioBytesLength) {
        this.audioBytesLength = audioBytesLength;
    }
}
