package com.marchina.agent;

import com.microsoft.cognitiveservices.speech.*;
import com.microsoft.cognitiveservices.speech.audio.*;
import io.github.cdimascio.dotenv.Dotenv;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;

@Service
public class STTAgent {
    private static final Logger logger = LoggerFactory.getLogger(STTAgent.class);
    
    private final String speechKey;
    private final String speechRegion;
    
    public STTAgent(Dotenv dotenv) {
        logger.info("Initializing STT Agent with Azure Speech Services");
        
        this.speechKey = dotenv.get("AZURE_SPEECH_KEY");
        this.speechRegion = dotenv.get("AZURE_SPEECH_REGION");
        
        logger.info("Azure Speech Region: {}", speechRegion);
        
        if (speechKey == null || speechRegion == null) {
            String message = "Missing required Azure Speech configuration. Please check your .env file.";
            logger.error(message);
            throw new IllegalStateException(message);
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
            // Create a temporary WAV file from the audio data
            File tempFile = createTempWavFile(audioData);
            
            // Configure speech recognition
            SpeechConfig speechConfig = SpeechConfig.fromSubscription(speechKey, speechRegion);
            speechConfig.setSpeechRecognitionLanguage("en-US");
            
            // Create audio configuration from the WAV file
            AudioConfig audioInput = AudioConfig.fromWavFileInput(tempFile.getAbsolutePath());
            
            // Create a speech recognizer
            SpeechRecognizer recognizer = new SpeechRecognizer(speechConfig, audioInput);
            
            // Start recognition and get the result
            StringBuilder resultBuilder = new StringBuilder();
            Semaphore stopRecognitionSemaphore = new Semaphore(0);
            
            // Subscribe to events
            recognizer.recognized.addEventListener((s, e) -> {
                if (e.getResult().getReason() == ResultReason.RecognizedSpeech) {
                    String text = e.getResult().getText();
                    logger.info("RECOGNIZED: {}", text);
                    resultBuilder.append(text).append(" ");
                } else if (e.getResult().getReason() == ResultReason.NoMatch) {
                    logger.warn("NOMATCH: Speech could not be recognized.");
                }
            });
            
            recognizer.canceled.addEventListener((s, e) -> {
                logger.info("CANCELED: Reason={}", e.getReason());
                if (e.getReason() == CancellationReason.Error) {
                    logger.error("CANCELED: ErrorCode={}", e.getErrorCode());
                    logger.error("CANCELED: ErrorDetails={}", e.getErrorDetails());
                }
                stopRecognitionSemaphore.release();
            });
            
            recognizer.sessionStopped.addEventListener((s, e) -> {
                logger.info("Session stopped event.");
                stopRecognitionSemaphore.release();
            });
            
            // Start continuous recognition
            recognizer.startContinuousRecognitionAsync().get();
            
            // Wait for recognition to complete
            stopRecognitionSemaphore.acquire();
            
            // Stop recognition
            recognizer.stopContinuousRecognitionAsync().get();
            
            // Clean up resources
            recognizer.close();
            audioInput.close();
            speechConfig.close();
            tempFile.delete();
            
            String result = resultBuilder.toString().trim();
            return result.isEmpty() ? "No transcription available" : result;
            
        } catch (Exception e) {
            logger.error("Error during speech transcription", e);
            throw new RuntimeException("Speech transcription failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Transcribes speech from microphone input
     * 
     * @return The transcribed text
     */
    public String transcribeAudio() {
        try {
            // Configure speech recognition
            SpeechConfig speechConfig = SpeechConfig.fromSubscription(speechKey, speechRegion);
            speechConfig.setSpeechRecognitionLanguage("en-US");
            
            // Create audio configuration from default microphone
            AudioConfig audioInput = AudioConfig.fromDefaultMicrophoneInput();
            
            // Create a speech recognizer
            SpeechRecognizer recognizer = new SpeechRecognizer(speechConfig, audioInput);
            
            logger.info("Speak into your microphone...");
            
            // Start recognition and get the result
            Future<SpeechRecognitionResult> task = recognizer.recognizeOnceAsync();
            SpeechRecognitionResult result = task.get();
            
            // Process the result
            if (result.getReason() == ResultReason.RecognizedSpeech) {
                String text = result.getText();
                logger.info("RECOGNIZED: {}", text);
                
                // Clean up resources
                recognizer.close();
                audioInput.close();
                speechConfig.close();
                
                return text;
            } else if (result.getReason() == ResultReason.NoMatch) {
                logger.warn("NOMATCH: Speech could not be recognized.");
                return "No speech could be recognized";
            } else if (result.getReason() == ResultReason.Canceled) {
                CancellationDetails cancellation = CancellationDetails.fromResult(result);
                logger.error("CANCELED: Reason={}", cancellation.getReason());
                
                if (cancellation.getReason() == CancellationReason.Error) {
                    logger.error("CANCELED: ErrorCode={}", cancellation.getErrorCode());
                    logger.error("CANCELED: ErrorDetails={}", cancellation.getErrorDetails());
                }
                
                return "Speech recognition was canceled: " + cancellation.getReason();
            }
            
            // Clean up resources
            recognizer.close();
            audioInput.close();
            speechConfig.close();
            
            return "Unexpected recognition result";
            
        } catch (Exception e) {
            logger.error("Error during speech transcription", e);
            throw new RuntimeException("Speech transcription failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Creates a temporary WAV file from audio data
     * 
     * @param audioData The audio data as byte array
     * @return The temporary file
     * @throws IOException If an I/O error occurs
     */
    private File createTempWavFile(byte[] audioData) throws IOException {
        File tempFile = File.createTempFile("audio_", ".wav");
        try (FileOutputStream fos = new FileOutputStream(tempFile)) {
            fos.write(audioData);
        }
        return tempFile;
    }
}
