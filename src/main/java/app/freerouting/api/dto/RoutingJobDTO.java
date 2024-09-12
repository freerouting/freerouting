package app.freerouting.api.dto;

import java.util.UUID;

public class RoutingJobDTO {
    public UUID id;
    public UUID sessionId;
    public String name;
    public JobState state;
    public JobPriority priority;
    public JobStage stage;

    public enum JobStage {
        IDLE,
        FANOUT,
        ROUTING,
        OPTIMIZATION,
        FINISHED,
        CANCELLED,
        ERROR
    }

    public enum JobPriority {
        LOWEST(0.0f), LOW(2.0f), BELOWNORMAL(4.0f), NORMAL(5.0f), ABOVENORMAL(6.0f), HIGH(8.0f), HIGHEST(10.0f);
        private final float value;

        JobPriority(float value) {
            this.value = Math.max(0.0f, Math.min(value, 10.0f));
        }

        public float getValue() {
            return value;
        }
    }

    public enum JobState {
        INVALID,
        QUEUED,
        READY_TO_START,
        RUNNING,
        PAUSED,
        COMPLETED,
        CANCELLED,
        TERMINATED
    }

    // Constructors
    public RoutingJobDTO() {}

    public RoutingJobDTO(UUID sessionId, String name) {
        this.sessionId = sessionId;
        this.name = name;
        this.state = JobState.QUEUED;
        this.priority = JobPriority.NORMAL;
        this.stage = JobStage.IDLE;
    }

    // Getters and setters
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public void setSessionId(UUID sessionId) {
        this.sessionId = sessionId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public JobState getState() {
        return state;
    }

    public void setState(JobState state) {
        this.state = state;
    }

    public JobPriority getPriority() {
        return priority;
    }

    public void setPriority(JobPriority priority) {
        this.priority = priority;
    }

    public JobStage getStage() {
        return stage;
    }

    public void setStage(JobStage stage) {
        this.stage = stage;
    }
}
