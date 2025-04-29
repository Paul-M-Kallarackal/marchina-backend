package com.marchina.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.cognitiveservices.speech.*;
import com.microsoft.cognitiveservices.speech.audio.*;
import io.github.cdimascio.dotenv.Dotenv;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SpeechWebSocketController extends BinaryWebSocketHandler {
    private static final Logger logger = LoggerFactory.getLogger(SpeechWebSocketController.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    private final String speechKey;
    private final String speechRegion;
    private final Map<WebSocketSession, PushAudioInputStream> sessionStreams = new ConcurrentHashMap<>();
    private final Map<WebSocketSession, SpeechRecognizer> sessionRecognizers = new ConcurrentHashMap<>();
    
    public SpeechWebSocketController(Dotenv dotenv) {
        this.speechKey = dotenv.get("AZURE_SPEECH_KEY");
        this.speechRegion = dotenv.get("AZURE_SPEECH_REGION");
        
        if (speechKey == null || speechRegion == null) {
            String message = "Missing required Azure Speech configuration. Please check your .env file.";
            logger.error(message);
            throw new IllegalStateException(message);
        }
    }
    
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        logger.info("WebSocket connection established: {}", session.getId());
        
        try {
            // Configure speech recognition
            SpeechConfig speechConfig = SpeechConfig.fromSubscription(speechKey, speechRegion);
            speechConfig.setSpeechRecognitionLanguage("en-US");
            
            // IMPORTANT: Explicitly set the audio format for the push stream
            AudioStreamFormat audioFormat = AudioStreamFormat.getWaveFormatPCM(16000, (short)16, (short)1);
            PushAudioInputStream audioStream = AudioInputStream.createPushStream(audioFormat);
            
            logger.info("Created push stream with format: 16kHz, 16-bit, mono PCM");
            
            AudioConfig audioConfig = AudioConfig.fromStreamInput(audioStream);
            
            // Increase the initial silence timeout
            speechConfig.setProperty(PropertyId.SpeechServiceConnection_InitialSilenceTimeoutMs, "10000"); // 10 seconds
            
            
            // Create a speech recognizer
            SpeechRecognizer recognizer = new SpeechRecognizer(speechConfig, audioConfig);
            
            // Subscribe to events
            recognizer.recognizing.addEventListener((s, e) -> {
                try {
                    // Send partial recognition results
                    if (e.getResult().getReason() == ResultReason.RecognizingSpeech) {
                        String text = e.getResult().getText();
                        logger.debug("RECOGNIZING: {}", text);
                        
                        Map<String, Object> response = new HashMap<>();
                        response.put("type", "transcription");
                        response.put("text", text);
                        response.put("isFinal", false);
                        
                        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
                    }
                } catch (Exception ex) {
                    logger.error("Error sending partial recognition result", ex);
                }
            });
            
            recognizer.recognized.addEventListener((s, e) -> {
                try {
                    // Send final recognition results
                    if (e.getResult().getReason() == ResultReason.RecognizedSpeech) {
                        String text = e.getResult().getText();
                        logger.info("RECOGNIZED: {}", text);
                        
                        Map<String, Object> response = new HashMap<>();
                        response.put("type", "transcription");
                        response.put("text", text);
                        response.put("isFinal", true);
                        
                        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
                    } else if (e.getResult().getReason() == ResultReason.NoMatch) {
                    // Get detailed NoMatch information
                    Map<String, Object> response = new HashMap<>();
                    // response.put("type", "error");
                    // response.put("message", "Speech recognition error: " + e.getResult().getReason());
                    NoMatchReason noMatchReason = NoMatchDetails.fromResult(e.getResult()).getReason();
                    // session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
                    session.close(CloseStatus.SERVER_ERROR);
                    
                    logger.warn("NOMATCH: Speech could not be recognized. Reason: {}", noMatchReason);
                    
                    // // Send the NoMatch reason to the client
                    // Map<String, Object> response = new HashMap<>();
                    // response.put("type", "nomatch");
                    // response.put("reason", noMatchReason.toString());
                    
                    // switch (noMatchReason) {
                    //     case NotRecognized:
                    //         response.put("message", "Speech was detected but not recognized");
                    //         break;
                    //     case InitialSilenceTimeout:
                    //         response.put("message", "No speech was detected within the initial silence timeout");
                    //         break;
                    //     case InitialBabbleTimeout:
                    //         response.put("message", "Too much noise was detected");
                    //         break;
                    //     case KeywordNotRecognized:
                    //         response.put("message", "Keyword was not recognized");
                    //         break;
                    //     default:
                    //         response.put("message", "Speech could not be recognized");
                    //         break;
                    // }
                    
                    // session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
                }
                } catch (Exception ex) {
                    logger.error("Error sending final recognition result", ex);
                }
            });
            
            recognizer.canceled.addEventListener((s, e) -> {
                try {
                    logger.info("CANCELED: Reason={}", e.getReason());
                    if (e.getReason() == CancellationReason.Error) {
                        logger.error("CANCELED: ErrorCode={}", e.getErrorCode());
                        logger.error("CANCELED: ErrorDetails={}", e.getErrorDetails());
                        
                        Map<String, Object> response = new HashMap<>();
                        response.put("type", "error");
                        response.put("message", "Speech recognition error: " + e.getErrorDetails());
                        
                        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
                    }
                } catch (Exception ex) {
                    logger.error("Error sending cancellation message", ex);
                }
            });
            
            // Start continuous recognition
            recognizer.startContinuousRecognitionAsync().get();
            
            // Store the stream and recognizer for this session
            sessionStreams.put(session, audioStream);
            sessionRecognizers.put(session, recognizer);
            
        } catch (Exception e) {
            logger.error("Error setting up speech recognition", e);
            Map<String, Object> response = new HashMap<>();
            response.put("type", "error");
            response.put("message", "Failed to initialize speech recognition: " + e.getMessage());
            
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
            session.close(CloseStatus.SERVER_ERROR);
        }
    }
    
    // In the handleBinaryMessage method:
    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) throws Exception {
        PushAudioInputStream audioStream = sessionStreams.get(session);
        if (audioStream != null) {
            ByteBuffer buffer = message.getPayload();
            byte[] audioData = new byte[buffer.remaining()];
            buffer.get(audioData);
            
            // Log the size of received audio data for debugging
            logger.debug("Received audio chunk of size: {} bytes", audioData.length);
            
            if (audioData.length > 0) {
                // Check if this is WebM audio data (it will have a header)
                // If it is, we need to extract the PCM data
                if (isWebMFormat(audioData)) {
                    try {
                        // This is a simplified approach - in production you'd use a proper WebM parser
                        // For now, we'll just log that we received WebM data
                        logger.debug("Received WebM format audio data");
                        
                        // In a real implementation, you would convert WebM to PCM here
                        // For this example, we'll just write the raw data and let the Speech SDK try to handle it
                        audioStream.write(audioData);
                    } catch (Exception e) {
                        logger.error("Error processing WebM audio data", e);
                    }
                } else {
                    // Assume it's already PCM or another format the Speech SDK can handle
                    audioStream.write(audioData);
                }
            }
        }
    }

    // Helper method to check if data appears to be in WebM format
    private boolean isWebMFormat(byte[] data) {
        // WebM files start with a specific header
        // This is a very simplified check - a real implementation would be more robust
        if (data.length > 4) {
            // Check for WebM header (0x1A 0x45 0xDF 0xA3)
            return data[0] == 0x1A && data[1] == 0x45 && data[2] == (byte)0xDF && data[3] == (byte)0xA3;
        }
        return false;
    }

    
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        logger.info("WebSocket connection closed: {}, status: {}", session.getId(), status);
        
        // Clean up resources
        PushAudioInputStream audioStream = sessionStreams.remove(session);
        if (audioStream != null) {
            audioStream.close();
        }
        
        SpeechRecognizer recognizer = sessionRecognizers.remove(session);
        if (recognizer != null) {
            recognizer.stopContinuousRecognitionAsync().get();
            recognizer.close();
        }
    }
    
    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        logger.error("WebSocket transport error: {}", exception.getMessage(), exception);
        
        // Clean up resources
        PushAudioInputStream audioStream = sessionStreams.remove(session);
        if (audioStream != null) {
            audioStream.close();
        }
        
        SpeechRecognizer recognizer = sessionRecognizers.remove(session);
        if (recognizer != null) {
            recognizer.stopContinuousRecognitionAsync().get();
            recognizer.close();
        }
    }
}
