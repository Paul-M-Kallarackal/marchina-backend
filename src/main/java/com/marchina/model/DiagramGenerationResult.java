package com.marchina.model;

/**
 * Represents the result of a diagram generation attempt by an agent.
 * Includes the generated name and Mermaid code on success, or an error message on failure.
 */
public record DiagramGenerationResult(
    String name,        // The LLM-generated diagram name (null on failure)
    String diagramCode, // The LLM-generated Mermaid code (null on failure)
    boolean success,    // Flag indicating if generation and validation succeeded
    String errorMessage // Error message if generation or validation failed (null on success)
) {

    /**
     * Creates a success result.
     *
     * @param name        The generated diagram name.
     * @param diagramCode The generated Mermaid code.
     * @return A successful DiagramGenerationResult.
     */
    public static DiagramGenerationResult success(String name, String diagramCode) {
        return new DiagramGenerationResult(name, diagramCode, true, null);
    }

    /**
     * Creates a failure result.
     *
     * @param errorMessage The error message describing the failure.
     * @return A failed DiagramGenerationResult.
     */
    public static DiagramGenerationResult failure(String errorMessage) {
        return new DiagramGenerationResult(null, null, false, errorMessage);
    }
} 