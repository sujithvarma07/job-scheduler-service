package com.sujith.scheduler.util;

public final class RetryUtil {

    private RetryUtil() {
    }

    /**
     * Computes the additional delay, in milliseconds, that should be added to a job's
     * queue score before it becomes eligible for retry. The delay grows exponentially
     * with the retry count so that repeatedly failing jobs back off further with each
     * successive attempt.
     *
     * @param retryCount the number of times the job has already been retried
     * @return the backoff delay, in milliseconds, to add to the job's queue score
     */
    public static double calculateBackoffScore(int retryCount) {
        return Math.pow(2, retryCount) * 1000.0;
    }
}
