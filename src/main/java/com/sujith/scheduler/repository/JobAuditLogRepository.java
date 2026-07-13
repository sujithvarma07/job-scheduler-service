package com.sujith.scheduler.repository;

import com.sujith.scheduler.model.JobAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface JobAuditLogRepository extends JpaRepository<JobAuditLog, UUID> {

    List<JobAuditLog> findByJobIdOrderByTimestampAsc(UUID jobId);
}
