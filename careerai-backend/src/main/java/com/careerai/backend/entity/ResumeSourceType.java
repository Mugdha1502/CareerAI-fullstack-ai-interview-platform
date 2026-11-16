package com.careerai.backend.entity;

/** How a resume was created. */
public enum ResumeSourceType {
    /** A PDF/DOC/DOCX file uploaded by the user; text is extracted on upload. */
    UPLOADED,
    /** Structured content written in the Resume Builder; text is rendered from it. */
    BUILDER
}
