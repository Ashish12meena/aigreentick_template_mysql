package com.aigreentick.services.template.application.port.out;

import java.util.List;

import com.aigreentick.services.template.application.dto.BatchUploadResult;
import com.aigreentick.services.template.application.service.DownloadedMediaTask;


public interface InternalMediaPort {
    BatchUploadResult uploadBatch(List<DownloadedMediaTask> tasks, Long orgId, Long projectId, String wabaId);
}
