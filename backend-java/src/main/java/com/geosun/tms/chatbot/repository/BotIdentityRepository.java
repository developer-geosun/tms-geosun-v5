package com.geosun.tms.chatbot.repository;

import com.geosun.tms.chatbot.domain.BotIdentity;
import com.geosun.tms.chatbot.domain.BotIdentityStatus;
import com.geosun.tms.chatbot.domain.ChatbotChannel;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BotIdentityRepository extends JpaRepository<BotIdentity, String> {

  Optional<BotIdentity> findByUserIdAndChannel(String userId, ChatbotChannel channel);

  Optional<BotIdentity> findByChannelAndExternalUserId(
      ChatbotChannel channel, String externalUserId);

  List<BotIdentity> findByUserId(String userId);

  List<BotIdentity> findByUserIdAndStatus(String userId, BotIdentityStatus status);

  List<BotIdentity> findByUserIdInAndStatus(Collection<String> userIds, BotIdentityStatus status);

  List<BotIdentity> findByChannelAndStatus(ChatbotChannel channel, BotIdentityStatus status);

  long countByEnterpriseIdAndChannelAndStatus(
      String enterpriseId, ChatbotChannel channel, BotIdentityStatus status);

  Page<BotIdentity> findByEnterpriseIdAndChannel(
      String enterpriseId, ChatbotChannel channel, Pageable pageable);

  Page<BotIdentity> findByEnterpriseId(String enterpriseId, Pageable pageable);
}
