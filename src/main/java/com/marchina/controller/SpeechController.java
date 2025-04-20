package com.marchina.controller;

import com.marchina.agent.STTAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/speech")
public class SpeechController {
    private static final Logger logger = LoggerFactory.getLogger(SpeechController.class);
    
    private final STTAgent SttAgent;
    
    public SpeechController(STTAgent SttAgent) {
        this.SttAgent = SttAgent;
    }
    
    @PostMapping("/transcribe")
    public ResponseEntity<String> transcribeAudio(@RequestParam("audio") MultipartFile audioFile) {
        try {
            logger.info("Received audio file for transcription: {}", audioFile.getOriginalFilename());
            byte[] audioData = audioFile.getBytes();
            String transcription = SttAgent.transcribeAudioData(audioData);
            return ResponseEntity.ok(transcription);
        } catch (IOException e) {
            logger.error("Error processing audio file", e);
            return ResponseEntity.badRequest().body("Error processing audio file: " + e.getMessage());
        }
    }
}
