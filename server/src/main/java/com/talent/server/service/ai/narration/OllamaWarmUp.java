package com.talent.server.service.ai.narration;

import com.talent.server.config.AiProperties;
import com.talent.server.enums.AiMode;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Loads the LLM in the background once the application is ready, so the first
 * agent run is not delayed.
 */
@Component
public class OllamaWarmUp {

    private final OllamaClient ollama;
    private final AiProperties properties;

    public OllamaWarmUp(OllamaClient ollama, AiProperties properties) {
        this.ollama = ollama;
        this.properties = properties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void warmUp() {
        if (properties.mode() == AiMode.SIMULATED || !properties.ollama().warmUpOnStartup()) {
            return;
        }
        Thread.ofVirtual().name("llm-warm-up").start(() -> {
            if (ollama.health().usable()) {
                ollama.warmUp();
            }
        });
    }
}
