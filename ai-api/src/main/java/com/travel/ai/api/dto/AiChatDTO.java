package com.travel.ai.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.List;
import java.util.UUID;

/**
 * DTOs for the AI conversational chat feature.
 * <p>
 * Callers supply the full conversation history on each turn so that the
 * service remains stateless – session continuity is the caller's responsibility.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public interface AiChatDTO {

    /**
     * Inbound chat request.
     *
     * @param itineraryId         UUID of the itinerary providing context;
     *                            must not be {@code null}
     * @param accessToken         caller's authentication token; must not be blank
     * @param message             the user's latest message; must not be blank
     * @param conversationHistory ordered list of prior turns in this conversation;
     *                            may be {@code null} or empty for the first turn
     */
    record Request(
            @NotNull(message = "itineraryId must not be null")
            UUID itineraryId,

            @NotBlank(message = "accessToken must not be blank")
            String accessToken,

            @NotBlank(message = "message must not be blank")
            String message,

            List<ChatMessage> conversationHistory
    ) {
        public Request {
            conversationHistory = conversationHistory == null
                    ? List.of()
                    : List.copyOf(conversationHistory);
        }
    }

    /**
     * Outbound chat response.
     *
     * @param reply       the AI assistant's textual reply; never {@code null}
     * @param suggestions proactive follow-up suggestions the user might ask next;
     *                    never {@code null}, may be empty
     */
    record Response(
            String reply,
            List<String> suggestions
    ) {
        public Response {
            suggestions = suggestions == null ? List.of() : List.copyOf(suggestions);
        }
    }

    /**
     * A single turn in the conversation history.
     *
     * @param role    the speaker's role; expected values are {@code "user"} or
     *                {@code "assistant"}
     * @param content the text content of this turn; must not be blank
     */
    record ChatMessage(
            @NotBlank
            @Pattern(regexp = "user|assistant",
                     message = "role must be 'user' or 'assistant'")
            String role,

            @NotBlank(message = "content must not be blank")
            String content
    ) {}
}
