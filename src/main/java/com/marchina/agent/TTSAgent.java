package com.marchina.agent;

import com.microsoft.cognitiveservices.speech.*;
import io.github.cdimascio.dotenv.Dotenv;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Base64;
import java.util.concurrent.ExecutionException;

@Component
public class TTSAgent {
    private static final Logger logger = LoggerFactory.getLogger(TTSAgent.class);
    
    private final String speechKey;
    private final String speechRegion;
    
    public TTSAgent(Dotenv dotenv) {
        logger.debug("Initializing TTSAgent");
        this.speechKey = dotenv.get("AZURE_SPEECH_KEY");
        this.speechRegion = dotenv.get("AZURE_SPEECH_REGION");
        
        logger.debug("Speech key present: {}", (speechKey != null && !speechKey.isEmpty()));
        logger.debug("Speech region: {}", speechRegion);
        
        if (speechKey == null || speechRegion == null) {
            String message = "Missing required Azure Speech configuration. Please check your .env file.";
            logger.error(message);
            throw new IllegalStateException(message);
        }
        
        logger.info("TTSAgent initialized successfully with region: {}", speechRegion);
    }
    
    /**
     * Converts text to speech using Azure Speech Service and returns the audio data as a Base64 encoded string
     *
     * @param text The text to convert to speech
     * @return Base64 encoded audio data
     * @throws IOException if there's an error generating the speech
     */
    public String generateSpeech(String text) throws IOException {
        logger.info("Starting speech generation for text: {}", text);
        
        if (text == null || text.trim().isEmpty()) {
            logger.error("Cannot generate speech for empty text");
            throw new IllegalArgumentException("Text cannot be empty");
        }
        
        SpeechConfig speechConfig = null;
        SpeechSynthesizer synthesizer = null;
        
        try {
            logger.debug("Creating speech config with key and region");
            speechConfig = SpeechConfig.fromSubscription(speechKey, speechRegion);
            
            logger.debug("Setting voice name to en-US-JennyNeural");
            speechConfig.setSpeechSynthesisVoiceName("en-US-JennyNeural");
            
            logger.debug("Setting output format to MP3");
            speechConfig.setSpeechSynthesisOutputFormat(SpeechSynthesisOutputFormat.Audio16Khz32KBitRateMonoMp3);
            
            logger.debug("Creating speech synthesizer");
            synthesizer = new SpeechSynthesizer(speechConfig);
            
            logger.info("Calling Azure Speech Service to synthesize text");
            SpeechSynthesisResult result = synthesizer.SpeakTextAsync(text).get();
            
            logger.debug("Speech synthesis completed with reason: {}", result.getReason());
            
            if (result.getReason() == ResultReason.SynthesizingAudioCompleted) {
                byte[] audioData = result.getAudioData();
                logger.debug("Received audio data of size: {} bytes", audioData.length);
                
                String base64Audio = Base64.getEncoder().encodeToString(audioData);
                logger.debug("Base64 encoded audio length: {}", base64Audio.length());
                
                logger.info("Speech synthesis succeeded");
                return base64Audio;
            } else {
                String errorDetails = result.getProperties().getProperty(PropertyId.SpeechServiceResponse_JsonErrorDetails);
                logger.error("Speech synthesis failed with reason: {}", result.getReason());
                logger.error("Error details: {}", errorDetails);
                throw new IOException("Speech synthesis failed: " + result.getReason());
            }
        } catch (InterruptedException e) {
            logger.error("Speech synthesis was interrupted: {}", e.getMessage(), e);
            Thread.currentThread().interrupt();
            throw new IOException("Speech synthesis was interrupted", e);
        } catch (ExecutionException e) {
            logger.error("Speech synthesis execution failed: {}", e.getMessage(), e);
            logger.error("Cause: {}", e.getCause() != null ? e.getCause().getMessage() : "Unknown");
            throw new IOException("Speech synthesis execution failed: " + e.getMessage(), e);
        } catch (Exception e) {
            logger.error("Unexpected error during speech synthesis: {}", e.getMessage(), e);
            throw new IOException("Unexpected error during speech synthesis: " + e.getMessage(), e);
        } finally {
            logger.debug("Cleaning up resources");
            if (synthesizer != null) {
                logger.debug("Closing synthesizer");
                synthesizer.close();
            }
            if (speechConfig != null) {
                logger.debug("Closing speech config");
                speechConfig.close();
            }
        }
    }
}
