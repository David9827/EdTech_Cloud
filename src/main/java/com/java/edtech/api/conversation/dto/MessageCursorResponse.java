package com.java.edtech.api.conversation.dto;

import java.util.List;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MessageCursorResponse {
    private List<MessageResponse> items;
    private String nextCursor;
    private boolean hasMore;
}
