package com.aigreentick.services.template.domain.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.aigreentick.services.template.domain.enums.TemplateCategory;
import com.aigreentick.services.template.domain.model.SystemTemplate;

/**
 * Read-only queries for Template Library entries. Not scoped by project:
 * library templates belong to the system, not to a tenant.
 */
@Repository
public interface SystemTemplateQueryRepository extends JpaRepository<SystemTemplate, Long> {

    Optional<SystemTemplate> findByIdAndActiveTrue(Long id);

    @Query("""
            SELECT s FROM SystemTemplate s
            WHERE s.active = true
              AND (:category IS NULL OR s.category = :category)
              AND (:language IS NULL OR s.language = :language)
              AND (:search IS NULL
                   OR LOWER(s.name) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(s.description) LIKE LOWER(CONCAT('%', :search, '%')))
            """)
    Page<SystemTemplate> findActiveByFilters(
            @Param("category") TemplateCategory category,
            @Param("language") String language,
            @Param("search") String search,
            Pageable pageable);

    /**
     * Clean 409 before the insert; {@code uk_system_template_name_lang} is the
     * real guarantee. {@code excludeId} lets an update ignore its own row.
     */
    @Query("""
            SELECT COUNT(s) > 0 FROM SystemTemplate s
            WHERE s.name = :name
              AND s.language = :language
              AND (:excludeId IS NULL OR s.id <> :excludeId)
            """)
    boolean existsByNameAndLanguageExcluding(
            @Param("name") String name,
            @Param("language") String language,
            @Param("excludeId") Long excludeId);
}
