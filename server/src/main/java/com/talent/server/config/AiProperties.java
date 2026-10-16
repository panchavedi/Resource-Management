package com.talent.server.config;

import com.talent.server.enums.AiMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Staffing agent configuration.
 *
 * @param mode            LIVE, SIMULATED or AUTO (LLM when reachable, otherwise
 *                        labelled simulation)
 * @param maxCandidates   how many ranked candidates the agent explains
 * @param maxLlmAttempts  LLM attempts (a retry receives the fact-checker's
 *                        feedback) before repair/fallback
 * @param narrationBudget a retry is only started when the projected total LLM
 *                        time stays within this budget
 * @param ollama          connection settings of the local Ollama server;
 *                        {@code keepAlive} keeps the model
 *                        loaded between requests and {@code warmUpOnStartup}
 *                        loads it when the app starts
 */
@Validated
@ConfigurationProperties("app.ai")
public record AiProperties(
                @NotNull @DefaultValue("AUTO") AiMode mode,
                @Min(1) @Max(10) @DefaultValue("3") int maxCandidates,
                @Min(1) @Max(3) @DefaultValue("2") int maxLlmAttempts,
                @NotNull @DefaultValue("150s") Duration narrationBudget,
                @Valid @NotNull @DefaultValue Ollama ollama) {

        public record Ollama(
                        @NotBlank @DefaultValue("http://localhost:11434") String baseUrl,
                        @NotBlank @DefaultValue("qwen2.5-coder:7b") String model,
                        @NotNull @DefaultValue("3s") Duration connectTimeout,
                        @NotNull @DefaultValue("240s") Duration readTimeout,
                        @DecimalMin("0") @DecimalMax("1") @DefaultValue("0.1") double temperature,
                        @Min(2048) @DefaultValue("4096") int contextWindow,
                        @NotBlank @DefaultValue("30m") String keepAlive,
                        @DefaultValue("true") boolean warmUpOnStartup) {
        }
}
