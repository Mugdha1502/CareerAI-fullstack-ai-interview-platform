package com.careerai.backend.service;

import com.careerai.backend.dto.ResumeContent;
import com.careerai.backend.dto.ResumeCreateRequest;
import com.careerai.backend.dto.ResumeResponse;
import com.careerai.backend.dto.ResumeUpdateRequest;
import com.careerai.backend.dto.ResumeVersionRequest;
import com.careerai.backend.dto.ResumeVersionResponse;
import com.careerai.backend.entity.Resume;
import com.careerai.backend.entity.ResumeSourceType;
import com.careerai.backend.entity.ResumeVersion;
import com.careerai.backend.entity.User;
import com.careerai.backend.exception.InvalidResumeRequestException;
import com.careerai.backend.exception.ResumeNotFoundException;
import com.careerai.backend.repository.JobMatchAnalysisRepository;
import com.careerai.backend.repository.ResumeAnalysisRepository;
import com.careerai.backend.repository.ResumeRepository;
import com.careerai.backend.repository.ResumeVersionRepository;
import com.careerai.backend.service.ResumeStorageService.StoredFile;
import com.careerai.backend.service.ResumeStorageService.ValidatedUpload;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

/**
 * Resume persistence for the authenticated user: upload, builder resumes, rename,
 * primary resume, delete, and versions. Every method takes the User resolved from the
 * JWT and loads resumes by (id, userId), so another user's resume is reported as 404.
 */
@Service
@RequiredArgsConstructor
public class ResumeService {

    static final int MAX_RESUMES_PER_USER = 50;
    static final int MAX_VERSIONS_PER_RESUME = 30;
    static final String ORIGINAL_VERSION_NAME = "Original Resume";

    private final ResumeRepository resumeRepository;
    private final ResumeVersionRepository versionRepository;
    private final ResumeAnalysisRepository analysisRepository;
    private final JobMatchAnalysisRepository jobMatchRepository;
    private final ResumeStorageService storageService;
    private final ResumeTextExtractionService extractionService;
    private final ObjectMapper objectMapper;

    /** A stored upload ready for an authenticated download response. */
    public record ResumeFile(Resource resource, String fileName, String contentType) {
    }


    /* ---------- Create ---------- */

    /** Validates, extracts text (fails before anything is stored), stores the file and saves the resume. */
    @Transactional
    public ResumeResponse upload(User user, MultipartFile file, String title) {
        ensureResumeCapacity(user);

        ValidatedUpload upload = storageService.validate(file);
        String text = extractionService.extract(upload.bytes(), upload.type());
        StoredFile stored = storageService.store(user.getId(), upload);

        // If the transaction rolls back, the file written above must not be left behind.
        deleteFileOnRollback(stored.storagePath());

        Resume resume = new Resume();
        resume.setUser(user);
        resume.setTitle(resolveTitle(title, stripExtension(upload.safeFileName())));
        resume.setOriginalFileName(upload.safeFileName());
        resume.setStoredFileName(stored.storedFileName());
        resume.setStoragePath(stored.storagePath());
        resume.setFileType(upload.type().name());
        resume.setFileSize((long) upload.bytes().length);
        resume.setExtractedText(text);
        resume.setSourceType(ResumeSourceType.UPLOADED);
        resume.setPrimary(resumeRepository.findTopByUserIdAndPrimaryTrue(user.getId()).isEmpty());

        Resume saved = resumeRepository.save(resume);
        saveVersion(saved, ORIGINAL_VERSION_NAME, null, text, 1);
        return ResumeResponse.detail(saved, null);
    }

    @Transactional
    public ResumeResponse createBuilderResume(User user, ResumeCreateRequest request) {
        ensureResumeCapacity(user);

        ResumeContent content = request.content().normalized();
        String json = writeContent(content);
        String text = content.toPlainText();

        Resume resume = new Resume();
        resume.setUser(user);
        resume.setTitle(resolveTitle(request.title(), "My Resume"));
        resume.setContent(json);
        resume.setExtractedText(text);
        resume.setSourceType(ResumeSourceType.BUILDER);
        resume.setPrimary(resumeRepository.findTopByUserIdAndPrimaryTrue(user.getId()).isEmpty());

        Resume saved = resumeRepository.save(resume);
        saveVersion(saved, ORIGINAL_VERSION_NAME, json, text, 1);
        return ResumeResponse.detail(saved, content);
    }


    /* ---------- Read ---------- */

    @Transactional(readOnly = true)
    public List<ResumeResponse> list(User user) {
        return resumeRepository.findByUserIdOrderByUpdatedAtDesc(user.getId()).stream()
            .map(ResumeResponse::summary)
            .toList();
    }

    @Transactional(readOnly = true)
    public ResumeResponse get(User user, Long resumeId) {
        Resume resume = requireOwnedResume(user, resumeId);
        return ResumeResponse.detail(resume, readContent(resume.getContent()));
    }

    @Transactional(readOnly = true)
    public ResumeFile loadFile(User user, Long resumeId) {
        Resume resume = requireOwnedResume(user, resumeId);
        if (resume.getSourceType() != ResumeSourceType.UPLOADED || resume.getStoragePath() == null) {
            throw new ResumeNotFoundException("This resume has no uploaded file");
        }

        String contentType = ResumeFileType.fromExtension(resume.getFileType() == null ? "" : resume.getFileType().toLowerCase())
            .map(ResumeFileType::mimeType)
            .orElse("application/octet-stream");

        return new ResumeFile(storageService.load(resume.getStoragePath()), resume.getOriginalFileName(), contentType);
    }


    /* ---------- Update ---------- */

    @Transactional
    public ResumeResponse update(User user, Long resumeId, ResumeUpdateRequest request) {
        Resume resume = requireOwnedResume(user, resumeId);

        if (request.title() == null && request.content() == null) {
            throw new InvalidResumeRequestException("Nothing to update. Provide a title or content.");
        }

        if (request.title() != null) {
            if (request.title().isBlank()) {
                throw new InvalidResumeRequestException("Resume title cannot be empty");
            }
            resume.setTitle(resolveTitle(request.title(), resume.getTitle()));
        }

        ResumeContent content = readContent(resume.getContent());

        if (request.content() != null) {
            if (resume.getSourceType() != ResumeSourceType.BUILDER) {
                throw new InvalidResumeRequestException("Uploaded resumes cannot be edited here. Upload a new file or create a builder resume.");
            }
            content = request.content().normalized();
            resume.setContent(writeContent(content));
            resume.setExtractedText(content.toPlainText());
        }

        // Saving through the repository flushes the change and bumps updatedAt.
        Resume saved = resumeRepository.saveAndFlush(resume);
        return ResumeResponse.detail(saved, content);
    }

    @Transactional
    public ResumeResponse setPrimary(User user, Long resumeId) {
        Resume resume = requireOwnedResume(user, resumeId);

        if (!resume.isPrimary()) {
            resumeRepository.clearPrimary(user.getId());
            resume = requireOwnedResume(user, resumeId);
            resume.setPrimary(true);
            resume = resumeRepository.saveAndFlush(resume);
        }

        return ResumeResponse.summary(resume);
    }


    /* ---------- Delete ---------- */

    /**
     * Deletes the resume with its versions, ATS analyses and job matches (in one transaction),
     * then removes the stored file once the transaction has committed.
     */
    @Transactional
    public void delete(User user, Long resumeId) {
        Resume resume = requireOwnedResume(user, resumeId);
        boolean wasPrimary = resume.isPrimary();
        String storagePath = resume.getStoragePath();

        jobMatchRepository.deleteByResumeId(resume.getId());
        analysisRepository.deleteByResumeId(resume.getId());
        versionRepository.deleteByResumeId(resume.getId());
        resumeRepository.delete(resume);
        resumeRepository.flush();

        if (wasPrimary) {
            resumeRepository.findTopByUserIdAndIdNotOrderByUpdatedAtDesc(user.getId(), resumeId)
                .ifPresent(next -> next.setPrimary(true));
        }

        deleteFileAfterCommit(storagePath);
    }


    /* ---------- Versions ---------- */

    @Transactional(readOnly = true)
    public List<ResumeVersionResponse> listVersions(User user, Long resumeId) {
        Resume resume = requireOwnedResume(user, resumeId);
        return versionRepository.findByResumeIdOrderByVersionNumberDesc(resume.getId()).stream()
            .map(version -> toVersionResponse(version, resume.getId()))
            .toList();
    }

    /** Snapshots the resume's current content as a new version. */
    @Transactional
    public ResumeVersionResponse createVersion(User user, Long resumeId, ResumeVersionRequest request) {
        Resume resume = requireOwnedResume(user, resumeId);
        ensureVersionCapacity(resume);

        int number = versionRepository.findMaxVersionNumber(resume.getId()) + 1;
        String name = request == null || request.versionName() == null || request.versionName().isBlank()
            ? "Version " + number
            : request.versionName().strip();

        ResumeVersion version = saveVersion(resume, name, resume.getContent(), resume.getExtractedText(), number);
        return toVersionResponse(version, resume.getId());
    }

    @Transactional
    public ResumeVersionResponse duplicateVersion(User user, Long resumeId, Long versionId) {
        Resume resume = requireOwnedResume(user, resumeId);
        ResumeVersion source = requireVersion(resume, versionId);
        ensureVersionCapacity(resume);

        int number = versionRepository.findMaxVersionNumber(resume.getId()) + 1;
        String name = truncate(source.getVersionName() + " (Copy)", 120);

        ResumeVersion copy = saveVersion(resume, name, source.getContent(), source.getExtractedText(), number);
        return toVersionResponse(copy, resume.getId());
    }

    @Transactional
    public ResumeVersionResponse renameVersion(User user, Long resumeId, Long versionId, ResumeVersionRequest request) {
        Resume resume = requireOwnedResume(user, resumeId);
        ResumeVersion version = requireVersion(resume, versionId);

        if (request == null || request.versionName() == null || request.versionName().isBlank()) {
            throw new InvalidResumeRequestException("Version name is required");
        }

        version.setVersionName(request.versionName().strip());
        return toVersionResponse(versionRepository.saveAndFlush(version), resume.getId());
    }

    @Transactional
    public void deleteVersion(User user, Long resumeId, Long versionId) {
        Resume resume = requireOwnedResume(user, resumeId);
        versionRepository.delete(requireVersion(resume, versionId));
    }


    /* ---------- Shared with analysis services ---------- */

    /** The caller's resume, or 404 (also for another user's id, so ids are not revealed). */
    @Transactional(readOnly = true)
    public Resume requireOwnedResume(User user, Long resumeId) {
        if (resumeId == null) {
            throw new ResumeNotFoundException("Resume not found");
        }
        return resumeRepository.findByIdAndUserId(resumeId, user.getId())
            .orElseThrow(() -> new ResumeNotFoundException("Resume not found"));
    }

    /** Resume text for analysis; a resume without readable text cannot be analysed. */
    public String requireText(Resume resume) {
        String text = resume.getExtractedText();
        if (text == null || text.chars().filter(Character::isLetter).count() < 40) {
            throw new InvalidResumeRequestException(
                "This resume does not have enough content to analyse yet. Add details in the Resume Builder or upload a text-based PDF/DOCX."
            );
        }
        return text;
    }


    /* ---------- helpers ---------- */

    private ResumeVersion requireVersion(Resume resume, Long versionId) {
        return versionRepository.findByIdAndResumeId(versionId, resume.getId())
            .orElseThrow(() -> new ResumeNotFoundException("Resume version not found"));
    }

    private ResumeVersion saveVersion(Resume resume, String name, String content, String text, int number) {
        ResumeVersion version = new ResumeVersion();
        version.setResume(resume);
        version.setVersionNumber(number);
        version.setVersionName(truncate(name, 120));
        version.setContent(content);
        version.setExtractedText(text);
        return versionRepository.save(version);
    }

    private ResumeVersionResponse toVersionResponse(ResumeVersion version, Long resumeId) {
        return ResumeVersionResponse.from(version, resumeId, readContent(version.getContent()));
    }

    private void ensureResumeCapacity(User user) {
        if (resumeRepository.countByUserId(user.getId()) >= MAX_RESUMES_PER_USER) {
            throw new InvalidResumeRequestException("You can keep up to " + MAX_RESUMES_PER_USER + " resumes. Delete one to add another.");
        }
    }

    private void ensureVersionCapacity(Resume resume) {
        if (versionRepository.countByResumeId(resume.getId()) >= MAX_VERSIONS_PER_RESUME) {
            throw new InvalidResumeRequestException("A resume can have up to " + MAX_VERSIONS_PER_RESUME + " versions. Delete one to add another.");
        }
    }

    private String writeContent(ResumeContent content) {
        return objectMapper.writeValueAsString(content);
    }

    private ResumeContent readContent(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, ResumeContent.class);
        } catch (JacksonException ex) {
            return null;
        }
    }

    private void deleteFileOnRollback(String storagePath) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    storageService.delete(storagePath);
                }
            }
        });
    }

    private void deleteFileAfterCommit(String storagePath) {
        if (storagePath == null) return;
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            storageService.delete(storagePath);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                storageService.delete(storagePath);
            }
        });
    }

    private static String resolveTitle(String requested, String fallback) {
        String title = requested == null || requested.isBlank() ? fallback : requested.strip();
        title = title == null || title.isBlank() ? "My Resume" : title.replaceAll("\\s+", " ");
        return truncate(title, 120);
    }

    private static String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max).strip();
    }
}
