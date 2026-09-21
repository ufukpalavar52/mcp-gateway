package com.mcpgateway.service.impl;

import com.mcpgateway.common.dto.PageResponse;
import com.mcpgateway.common.exception.ResourceNotFoundException;
import com.mcpgateway.domain.entity.Run;
import com.mcpgateway.domain.enums.RunStatus;
import com.mcpgateway.dto.response.RunResponse;
import com.mcpgateway.mapper.RunMapper;
import com.mcpgateway.queue.CancellationPublisher;
import com.mcpgateway.repository.RunRepository;
import com.mcpgateway.security.SecurityUtils;
import com.mcpgateway.service.intf.AuditService;
import com.mcpgateway.service.intf.RunService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Reading dispatched work, and asking for it to stop. */
@Slf4j
@Service
@RequiredArgsConstructor
public class RunServiceImpl implements RunService {

    private final RunRepository runRepository;
    private final com.mcpgateway.repository.DefinitionRepository definitionRepository;
    private final com.mcpgateway.service.DefinitionAccessGuard access;
    private final RunMapper mapper;
    private final CancellationPublisher cancellationPublisher;
    private final AuditService auditService;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<RunResponse> findAll(Pageable pageable) {
        // The output of a definition somebody cannot reach is not theirs to read either:
        // a command they may not run still printed what it printed, and the run history is
        // where it stays afterwards.
        if (access.unrestricted()) {
            return PageResponse.from(runRepository.findAllBy(pageable), mapper::toResponse);
        }

        Set<Long> allowed = access.runnableIds(definitionRepository.findAll());
        List<Run> mine = runRepository.findAll().stream()
                .filter(run -> run.getDefinition() == null
                        || allowed.contains(run.getDefinition().getId()))
                .sorted(java.util.Comparator.comparing(Run::getId).reversed())
                .toList();

        int from = (int) Math.min(pageable.getOffset(), mine.size());
        int to = Math.min(from + pageable.getPageSize(), mine.size());

        return new PageResponse<>(
                mine.subList(from, to).stream().map(mapper::toResponse).toList(),
                pageable.getPageNumber(),
                pageable.getPageSize(),
                mine.size(),
                (int) Math.ceil((double) mine.size() / pageable.getPageSize()),
                to >= mine.size());
    }

    @Override
    @Transactional(readOnly = true)
    public RunResponse findByRef(String runRef) {
        List<Run> runs = runRepository.findByRunRefOrderByIdAsc(runRef);

        if (runs.isEmpty()) {
            throw new ResourceNotFoundException("Run", runRef);
        }
        // One job can have several actions, and a job approved whole always does: "write the
        // script" and "run the script" are one decision and one reference. Returning the
        // first alone showed the console the write — which prints nothing — while the
        // output of the command that produced something was never on screen.
        //
        // The first is still the one the caller named, so the fields at the top are its
        // fields; the rest travel alongside for a screen that wants to show them all.
        List<RunResponse> steps = runs.stream().map(mapper::toResponse).toList();
        RunResponse first = steps.getFirst();

        return steps.size() == 1
                ? first
                : new RunResponse(
                        first.id(), first.runRef(), first.actionRef(), first.definitionId(),
                        first.toolName(), first.actionName(), first.actorLabel(),
                        first.purpose(), first.status(), first.error(), first.statement(),
                        first.startedAt(), first.finishedAt(), first.targets(), steps,
                        // Any step of a job waiting on an executor that is not there makes
                        // the whole job stuck: they share one queue, and a later action
                        // cannot start until the one in front of it reports.
                        steps.stream().anyMatch(RunResponse::stalled));
    }

    @Override
    @Transactional
    public int cancel(String runRef, String reason) {
        List<Run> runs = runRepository.findByRunRefOrderByIdAsc(runRef);

        if (runs.isEmpty()) {
            throw new ResourceNotFoundException("Run", runRef);
        }

        long unfinished = runs.stream().filter(run -> !isFinished(run.getStatus())).count();

        if (unfinished == 0) {
            // Broadcasting anyway would be harmless but misleading: the audit trail would
            // show a cancellation for work that had already ended.
            log.info("Run {} has already finished; nothing to cancel", runRef);
            return 0;
        }

        String actor = SecurityUtils.currentActorLabel();
        cancellationPublisher.cancel(runRef, actor, reason);

        auditService.record("run.cancellation_requested", "run", runs.getFirst().getId(),
                Map.of("runRef", runRef, "actions", unfinished,
                        "reason", reason == null ? "" : reason));

        // The rows are left alone on purpose. Whether the work stopped is the executor's to
        // report, and writing "cancelled" here would be a claim rather than a record — a run
        // that had already completed on the far side would be filed as cancelled forever.
        return (int) unfinished;
    }

    private boolean isFinished(RunStatus status) {
        return status == RunStatus.SUCCEEDED
                || status == RunStatus.FAILED
                || status == RunStatus.CANCELLED;
    }
}
