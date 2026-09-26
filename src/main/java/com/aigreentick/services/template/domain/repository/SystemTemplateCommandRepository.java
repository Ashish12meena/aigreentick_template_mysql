package com.aigreentick.services.template.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.aigreentick.services.template.domain.model.SystemTemplate;

/**
 * Write operations for Template Library entries — reads go to
 * {@link SystemTemplateQueryRepository}. Library entries are never deleted;
 * they are deactivated ({@code is_active = 0}) instead.
 */
@Repository
public interface SystemTemplateCommandRepository extends JpaRepository<SystemTemplate, Long> {
}
