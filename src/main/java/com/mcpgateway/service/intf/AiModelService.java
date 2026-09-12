package com.mcpgateway.service.intf;

import com.mcpgateway.dto.request.AiModelRequest;
import com.mcpgateway.dto.response.AiModelResponse;

import java.util.List;

/** Registry of language model connections. */
public interface AiModelService {

    List<AiModelResponse> findAll();

    AiModelResponse findById(Long id);

    AiModelResponse create(AiModelRequest request);

    AiModelResponse update(Long id, AiModelRequest request);

    /** Flips the enabled flag and returns the new state. */
    AiModelResponse toggleEnabled(Long id);

    /** Refuses to delete while definitions still reference the model. */
    void delete(Long id);
}
