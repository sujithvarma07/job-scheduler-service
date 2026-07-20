package com.sujith.scheduler.repository;

import com.sujith.scheduler.model.Job;
import com.sujith.scheduler.model.JobStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface JobRepository extends JpaRepository<Job, UUID> {

    @Query("SELECT j FROM Job j WHERE j.status = :status ORDER BY j.priority DESC, j.createdAt ASC")
    List<Job> findByStatusOrderByPriorityDescCreatedAtAsc(JobStatus status);

    Page<Job> findByStatus(JobStatus status, Pageable pageable);

    List<Job> findByStatusIn(List<JobStatus> statuses);

    @Query("SELECT j FROM Job j WHERE j.status = com.sujith.scheduler.model.JobStatus.RUNNING "
            + "AND j.startedAt IS NOT NULL")
    List<Job> findRunningJobs();

    @Query("SELECT j.status, COUNT(j) FROM Job j GROUP BY j.status")
    List<Object[]> countJobsGroupedByStatus();
}
