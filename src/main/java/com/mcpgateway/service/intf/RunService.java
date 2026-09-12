package com.mcpgateway.service.intf;

import com.mcpgateway.dto.response.RunResponse;
import com.mcpgateway.common.dto.PageResponse;
import org.springframework.data.domain.Pageable;

/** Reading and stopping the work that was dispatched. */
public interface RunService {

    PageResponse<RunResponse> findAll(Pageable pageable);

    RunResponse findByRef(String runRef);

    /**
     * Asks the executors to stop a run.
     *
     * <p>Returns how many rows the request covers. The rows are not marked here: whether the
     * work stopped is the executor's to report, and writing "cancelled" before knowing would
     * be a claim rather than a record.
     */
    int cancel(String runRef, String reason);
}
