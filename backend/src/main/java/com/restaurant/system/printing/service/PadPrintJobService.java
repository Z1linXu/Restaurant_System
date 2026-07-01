package com.restaurant.system.printing.service;

import com.restaurant.system.printing.dto.PadPrintJobClaimRequest;
import com.restaurant.system.printing.dto.PadPrintJobCompleteRequest;
import com.restaurant.system.printing.dto.PadPrintJobFailRequest;
import com.restaurant.system.printing.dto.PadPrintJobPayloadResponse;
import com.restaurant.system.printing.dto.PadPrintJobReleaseRequest;
import com.restaurant.system.printing.dto.PrintJobResponse;
import com.restaurant.system.printing.entity.StoreDevice;
import java.util.List;

public interface PadPrintJobService {

    List<PrintJobResponse> listPendingJobs(StoreDevice device, Long storeId, int limit);

    PrintJobResponse claimJob(StoreDevice device, Long jobId, PadPrintJobClaimRequest request);

    PadPrintJobPayloadResponse getPayload(StoreDevice device, Long jobId);

    PrintJobResponse completeJob(StoreDevice device, Long jobId, PadPrintJobCompleteRequest request);

    PrintJobResponse failJob(StoreDevice device, Long jobId, PadPrintJobFailRequest request);

    PrintJobResponse releaseJob(StoreDevice device, Long jobId, PadPrintJobReleaseRequest request);
}
