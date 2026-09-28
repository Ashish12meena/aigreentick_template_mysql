package com.apargo.services.template.application.port.out;

import java.util.List;

import com.apargo.services.template.application.dto.BatchUploadResult;
import com.apargo.services.template.application.service.DownloadedMediaTask;


public interface InternalMediaPort {
    BatchUploadResult uploadBatch(List<DownloadedMediaTask> tasks, Long orgId, Long projectId, String wabaId);
}
