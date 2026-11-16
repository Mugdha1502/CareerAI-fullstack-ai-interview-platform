package com.careerai.backend.repository;

import com.careerai.backend.entity.CoachConversation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** Always scoped by user id: one user never sees another user's conversations. */
public interface CoachConversationRepository extends JpaRepository<CoachConversation, Long> {

    List<CoachConversation> findTop50ByUserIdOrderByUpdatedAtDesc(Long userId);

    Optional<CoachConversation> findByIdAndUserId(Long id, Long userId);
}
