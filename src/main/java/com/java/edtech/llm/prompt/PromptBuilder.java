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
        if (mode == LlmMode.STORY_QA) {
            return "You are a warm Vietnamese storytelling robot for children answering questions during a story. "
                    + "Use only provided story context up to current segment, never spoil future story parts. "
                    + "If question is beyond current context, say that part has not happened yet and invite child to continue story. "
                    + "Reply very briefly in plain Vietnamese text only, easy words for children. "
                    + "End with a short transition to continue story.";
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
        if (mode == LlmMode.STORY_QA) {
            return "Story Q and A context: " + input
                    + "\nAnswer in 1 to 3 short Vietnamese sentences, plain text only, then add one short line to continue story.";
        }
        return "Child said: " + input
                + "\nReply briefly in Vietnamese, keep it age-appropriate, and return plain text only.";
    }
}
