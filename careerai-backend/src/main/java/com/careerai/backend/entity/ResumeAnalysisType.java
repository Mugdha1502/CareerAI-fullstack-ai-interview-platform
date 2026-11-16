package com.careerai.backend.entity;

/**
 * Kind of analysis stored in resume_analyses.
 * Job matches are stored in job_match_analyses (they also keep the job description);
 * JOB_MATCH and AI_IMPROVEMENT are reserved for the AI integration phase.
 */
public enum ResumeAnalysisType {
    ATS,
    JOB_MATCH,
    AI_IMPROVEMENT
}
