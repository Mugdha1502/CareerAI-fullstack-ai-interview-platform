package com.careerai.backend.repository;

import com.careerai.backend.entity.CoachMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/** Callers resolve the conversation by (id, userId) first. */
public interface CoachMessageRepository extends JpaRepository<CoachMessage, Long> {

    List<CoachMessage> findByConversationIdOrderByIdAsc(Long conversationId);

    /** Newest first; reverse for chronological order. Used for the bounded AI memory window. */
    List<CoachMessage> findByConversationIdOrderByIdDesc(Long conversationId, Pageable pageable);

    long countByConversationId(Long conversationId);

    @Modifying
    @Query("delete from CoachMessage m where m.conversation.id = :conversationId")
    int deleteByConversationId(@Param("conversationId") Long conversationId);
}
