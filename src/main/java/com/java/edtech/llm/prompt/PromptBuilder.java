package com.java.edtech.llm.prompt;

import org.springframework.stereotype.Component;

@Component
public class PromptBuilder {
    public String buildSystemPrompt(LlmMode mode) {
        if (mode == LlmMode.STORY) {
            return "You are a warm Vietnamese storytelling robot for children. "
                    + "Tell short, age-appropriate story parts in plain Vietnamese text only. "
                    + "Do not use markdown, emoji, or special symbols such as **, !, @, %, #, &, $, (, ).";
        }
        return "You are a safe and friendly Vietnamese educational robot assistant for children. "
                + "Output must be plain Vietnamese text only. "
                + "Do not use markdown, emoji, or special symbols such as **, !, @, %, #, &, $, (, ).";
    }

    public String buildUserPrompt(LlmMode mode, String input) {
        if (mode == LlmMode.STORY) {
            return "Context/story input: " + input
                    + "\nContinue the story briefly in Vietnamese, simple words for children, plain text only.";
        }
        return "Child said: " + input
                + "\nReply briefly in Vietnamese, keep it age-appropriate, and return plain text only.";
    }
}
