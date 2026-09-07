package com.gaozhaoyang.agent.common;

import com.gaozhaoyang.agent.requirement.RequirementAnalysisException;
import com.gaozhaoyang.agent.solution.SolutionGenerationException;
import com.gaozhaoyang.agent.workflow.InvalidWorkflowStateException;
import com.gaozhaoyang.agent.workflow.WorkflowNotFoundException;
import com.gaozhaoyang.agent.workflow.WorkflowPersistenceException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError handleValidation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage())
                .orElse("请求参数不合法");
        return new ApiError("INVALID_REQUEST", 
        message, 
        Instant.now());
    }

    @ExceptionHandler(RequirementAnalysisException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ApiError handleRequirementAnalysis(
            RequirementAnalysisException exception) {
        return new ApiError(
                "AI_SERVICE_UNAVAILABLE",
                exception.getMessage(),
                Instant.now()
        );
    }

    @ExceptionHandler(SolutionGenerationException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ApiError handleSolutionGeneration(
            SolutionGenerationException exception
    ) {
        return new ApiError(
                "SOLUTION_GENERATION_UNAVAILABLE",
                exception.getMessage(),
                Instant.now()
        );
    }

    @ExceptionHandler(WorkflowNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiError handleWorkflowNotFound(
            WorkflowNotFoundException exception
    ) {
        return new ApiError(
                "WORKFLOW_NOT_FOUND",
                exception.getMessage(),
                Instant.now()
        );
    }

    @ExceptionHandler(InvalidWorkflowStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiError handleInvalidWorkflowState(
            InvalidWorkflowStateException exception
    ) {
        return new ApiError(
                "INVALID_WORKFLOW_STATE",
                exception.getMessage(),
                Instant.now()
        );
    }

    @ExceptionHandler(WorkflowPersistenceException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ApiError handleWorkflowPersistence(
            WorkflowPersistenceException exception
    ) {
        return new ApiError(
                "WORKFLOW_STORAGE_UNAVAILABLE",
                exception.getMessage(),
                Instant.now()
        );
    }
}
