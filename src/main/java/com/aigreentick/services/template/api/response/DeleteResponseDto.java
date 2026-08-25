package com.aigreentick.services.template.api.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class DeleteResponseDto {
    private int deletedCount;
    private Long projectId;
    private Long templateId; // null for bulk delete
}