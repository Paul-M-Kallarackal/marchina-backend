package com.marchina.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.cdimascio.dotenv.Dotenv;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.sound.sampled.*;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Service
public class STTAgent {
    private static final Logger logger = LoggerFactory.getLogger(STTAgent.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    private final String apiKey;
    private final String endpoint;
    private final String deploymentId;
    private final String apiVersion;
    
    private static final AudioFormat AUDIO_FORMAT = new AudioFormat(
            16000, // Sample rate
            16,    // Sample size in bits
            1,     // Channels
            true,  // Signed
            false  // Little endian
    );
    
    public STTAgent(Dotenv dotenv) {
        logger.info("Initializing STT Agent with Azure OpenAI");
        
        this.apiKey = dotenv.get("AZURE_OPENAI_API_KEY");
        this.endpoint = dotenv.get("AZURE_OPENAI_ENDPOINT");
        this.deploymentId = dotenv.get("AZURE_OPENAI_REALTIME_DEPLOYMENT_ID");
        this.apiVersion = dotenv.get("AZURE_OPENAI_API_VERSION");
        
        logger.info("Azure OpenAI Endpoint: {}", endpoint);
        logger.info("Azure OpenAI DeploymentId: {}", deploymentId);
        logger.info("Azure OpenAI API Version: {}", apiVersion);
        
        if (apiKey == null || endpoint == null || deploymentId == null || apiVersion == null) {
            String message = "Missing required Azure OpenAI configuration. Please check your .env file.";
            logger.error(message);
            throw new IllegalStateException(message);
        }
    }
    
    /**
     * Transcribes speech from audio input using Azure OpenAI's realtime API
     * 
     * @return The transcribed text
     */
    public String transcribeAudio() {
        try {
            // Create WebSocket connection to Azure OpenAI realtime endpoint
            String wsUrl = constructWebSocketUrl();
            STTWebSocketClient client = new STTWebSocketClient(new URI(wsUrl), apiKey);
            
            // Connect to the WebSocket
            if (!client.connectBlocking(30, TimeUnit.SECONDS)) {
                throw new RuntimeException("Failed to connect to Azure OpenAI realtime endpoint");
            }
            
            // Start audio capture and streaming
            return captureAndStreamAudio(client);
            
        } catch (Exception e) {
            logger.error("Error during speech transcription", e);
            throw new RuntimeException("Speech transcription failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Transcribes speech from a pre-recorded audio file
     * 
     * @param audioData The audio data as byte array
     * @return The transcribed text
     */
    public String transcribeAudioData(byte[] audioData) {
        try {
            // Create WebSocket connection to Azure OpenAI realtime endpoint
            String wsUrl = constructWebSocketUrl();
            STTWebSocketClient client = new STTWebSocketClient(new URI(wsUrl), apiKey);
            
            // Connect to the WebSocket
            if (!client.connectBlocking(30, TimeUnit.SECONDS)) {
                throw new RuntimeException("Failed to connect to Azure OpenAI realtime endpoint");
            }
            
            // Stream the audio data
            return streamAudioData(client, audioData);
            
        } catch (Exception e) {
            logger.error("Error during speech transcription", e);
            throw new RuntimeException("Speech transcription failed: " + e.getMessage(), e);
        }
    }
    
    private String constructWebSocketUrl() {
        return String.format("wss://%s/openai/realtime?api-version=%s&deployment=%s", 
                endpoint.replace("https://", "").replace("http://", ""),
                apiVersion, 
                deploymentId);
    }
    
    private String captureAndStreamAudio(STTWebSocketClient client) throws Exception {
        // Configure audio capture
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, AUDIO_FORMAT);
        if (!AudioSystem.isLineSupported(info)) {
            throw new RuntimeException("Audio line not supported");
        }
        
        TargetDataLine line = (TargetDataLine) AudioSystem.getLine(info);
        line.open(AUDIO_FORMAT);
        line.start();
        
        logger.info("Started audio capture. Speak now...");
        
        // Send start message to initialize the session
        sendStartMessage(client);
        
        // Buffer for reading audio data
        byte[] buffer = new byte[4096];
        ByteArrayOutputStream audioData = new ByteArrayOutputStream();
        
        // Set up a future to get the transcription result
        CompletableFuture<String> transcriptionFuture = client.getTranscriptionFuture();
        
        try {
            // Read and stream audio for up to 30 seconds or until silence is detected
            long startTime = System.currentTimeMillis();
            int silenceCounter = 0;
            
            while (System.currentTimeMillis() - startTime < 30000 && !transcriptionFuture.isDone()) {
                int bytesRead = line.read(buffer, 0, buffer.length);
                
                if (bytesRead > 0) {
                    // Check if this is silence (simplified check)
                    boolean isSilence = isSilence(buffer, bytesRead);
                    
                    if (isSilence) {
                        silenceCounter++;
                        // If 1.5 seconds of silence (assuming ~15 frames per second)
                        if (silenceCounter > 15) {
                            break;
                        }
                    } else {
                        silenceCounter = 0;
                    }
                    
                    // Store the audio data
                    audioData.write(buffer, 0, bytesRead);
                    
                    // Send audio chunk to Azure
                    sendAudioChunk(client, buffer, bytesRead);
                }
            }
            
            // Send end message
            sendEndMessage(client);
            
            // Wait for final transcription
            String result = transcriptionFuture.get(5, TimeUnit.SECONDS);
            return result != null ? result : "No transcription available";
            
        } finally {
            line.stop();
            line.close();
            client.closeBlocking();
        }
    }
    
    private String streamAudioData(STTWebSocketClient client, byte[] audioData) throws Exception {
        // Send start message to initialize the session
        sendStartMessage(client);
        
        // Stream audio data in chunks
        int chunkSize = 4096;
        for (int i = 0; i < audioData.length; i += chunkSize) {
            int length = Math.min(chunkSize, audioData.length - i);
            byte[] chunk = new byte[length];
            System.arraycopy(audioData, i, chunk, 0, length);
            sendAudioChunk(client, chunk, length);
            
            // Small delay to avoid overwhelming the API
            Thread.sleep(10);
        }
        
        // Send end message
        sendEndMessage(client);
        
        // Wait for final transcription
        CompletableFuture<String> transcriptionFuture = client.getTranscriptionFuture();
        String result = transcriptionFuture.get(5, TimeUnit.SECONDS);
        client.closeBlocking();
        
        return result != null ? result : "No transcription available";
    }
    
    // Updated sendStartMessage for GPT-4o
    private void sendStartMessage(STTWebSocketClient client) throws Exception {
        Map<String, Object> message = new HashMap<>();
        message.put("type", "start");

        Map<String, Object> inputFormat = new HashMap<>();
        inputFormat.put("encoding", "pcm");
        inputFormat.put("sample_rate", 16000);
        inputFormat.put("channels", 1);

        Map<String, Object> responseFormat = new HashMap<>();
        responseFormat.put("type", "text");

        message.put("input_format", inputFormat);
        message.put("response_format", responseFormat);

        client.send(objectMapper.writeValueAsString(message));
    }

    // Updated sendAudioChunk to send raw binary data instead of base64
    private void sendAudioChunk(STTWebSocketClient client, byte[] audioChunk, int length) throws Exception {
        byte[] chunk = new byte[length];
        System.arraycopy(audioChunk, 0, chunk, 0, length);
        client.send(chunk);  // Send as raw bytes
    }

    
    private void sendEndMessage(STTWebSocketClient client) throws Exception {
        Map<String, Object> message = new HashMap<>();
        message.put("type", "end");
        
        client.send(objectMapper.writeValueAsString(message));
    }
    
    private boolean isSilence(byte[] buffer, int bytesRead) {
        // Simple silence detection - check if audio amplitude is below threshold
        int threshold = 500; // Adjust based on your needs
        
        for (int i = 0; i < bytesRead; i += 2) {
            if (i + 1 < bytesRead) {
                int sample = ((buffer[i] & 0xFF) | (buffer[i + 1] << 8));
                if (Math.abs(sample) > threshold) {
                    return false;
                }
            }
        }
        return true;
    }
    
    /**
     * WebSocket client for handling Azure OpenAI realtime API communication
     */
    private static class STTWebSocketClient extends WebSocketClient {
        private final Logger logger = LoggerFactory.getLogger(STTWebSocketClient.class);
        private final CompletableFuture<String> transcriptionFuture = new CompletableFuture<>();
        private final StringBuilder transcriptionBuilder = new StringBuilder();
        private final String apiKey;
        
        public STTWebSocketClient(URI serverUri, String apiKey) {
            super(serverUri);
            this.apiKey = apiKey;
            
            // Add Azure API key as header
            this.addHeader("api-key", apiKey);
        }
        
        @Override
        public void onOpen(ServerHandshake handshakedata) {
            logger.info("Connected to Azure OpenAI realtime endpoint");
        }
        
        @Override
        public void onMessage(String message) {
            try {
                Map<String, Object> response = objectMapper.readValue(message, Map.class);
                String type = (String) response.get("type");
                
                if ("partial".equals(type)) {
                    // Handle partial transcription
                    String text = (String) response.get("text");
                    logger.debug("Partial transcription: {}", text);
                } else if ("final".equals(type)) {
                    // Handle final transcription
                    String text = (String) response.get("text");
                    logger.info("Final transcription: {}", text);
                    transcriptionBuilder.append(text).append(" ");
                } else if ("error".equals(type)) {
                    // Handle error
                    String errorMessage = (String) response.get("message");
                    logger.error("Error from Azure OpenAI: {}", errorMessage);
                    transcriptionFuture.completeExceptionally(new RuntimeException(errorMessage));
                } else if ("end".equals(type)) {
                    // End of session
                    logger.info("Transcription session ended");
                    transcriptionFuture.complete(transcriptionBuilder.toString().trim());
                }
            } catch (Exception e) {
                logger.error("Error processing message from Azure OpenAI", e);
                transcriptionFuture.completeExceptionally(e);
            }
        }
        
        @Override
        public void onClose(int code, String reason, boolean remote) {
            logger.info("Connection closed: {} - {}", code, reason);
            if (!transcriptionFuture.isDone()) {
                if (transcriptionBuilder.length() > 0) {
                    transcriptionFuture.complete(transcriptionBuilder.toString().trim());
                } else {
                    transcriptionFuture.completeExceptionally(
                            new RuntimeException("Connection closed without transcription: " + reason));
                }
            }
        }
        
        @Override
        public void onError(Exception ex) {
            logger.error("WebSocket error", ex);
            transcriptionFuture.completeExceptionally(ex);
        }
        
        @Override
        public void onMessage(ByteBuffer bytes) {
            logger.debug("Received binary message");
        }
        
        public CompletableFuture<String> getTranscriptionFuture() {
            return transcriptionFuture;
        }
    }
}
