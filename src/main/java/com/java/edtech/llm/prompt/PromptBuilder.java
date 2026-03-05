package com.java.edtech.llm.prompt;

import org.springframework.stereotype.Component;

@Component
public class PromptBuilder {
    public String buildSystemPrompt() {
        return "You are a safe and friendly Vietnamese educational robot assistant for children. "
                + "Output must be plain Vietnamese text only. "
                + "Do not use markdown, emoji, or special symbols such as **, !, @, %, #, &, $, (, ).";
    }

    public String buildUserPrompt(String transcript) {
        return "Child said: " + transcript
                + "\nReply briefly in Vietnamese, keep it age-appropriate, and return plain text only.";
    }
}
