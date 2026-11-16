package com.careerai.backend.service;

import com.careerai.backend.exception.ResumeTextExtractionException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;

/**
 * Extracts plain text from PDF (Apache PDFBox), DOCX and DOC (Apache POI).
 * Text-based documents only: scanned/image resumes would need OCR, which is not used.
 */
@Service
public class ResumeTextExtractionService {

    private static final Logger log = LoggerFactory.getLogger(ResumeTextExtractionService.class);

    /** Longer text is cut; real resumes are far shorter. */
    static final int MAX_TEXT_LENGTH = 100_000;

    /** Below this many letters the document is treated as having no readable text. */
    private static final int MIN_LETTERS = 40;

    public String extract(byte[] bytes, ResumeFileType type) {
        String raw;

        try {
            raw = switch (type) {
                case PDF -> extractPdf(bytes);
                case DOCX -> extractDocx(bytes);
                case DOC -> extractDoc(bytes);
            };
        } catch (InvalidPasswordException ex) {
            throw new ResumeTextExtractionException("This PDF is password-protected. Please upload an unprotected copy.");
        } catch (IOException | RuntimeException ex) {
            log.info("Resume text extraction failed for a {} file: {}", type, ex.getClass().getSimpleName());
            throw new ResumeTextExtractionException(
                "Could not read text from this " + type.name() + " file. It may be damaged or in an unsupported format."
            );
        }

        String text = clean(raw);

        if (text.chars().filter(Character::isLetter).count() < MIN_LETTERS) {
            throw new ResumeTextExtractionException(
                "No readable text was found in this file. Scanned or image-only resumes are not supported yet; please upload a text-based PDF or DOCX."
            );
        }

        return text;
    }

    private String extractPdf(byte[] bytes) throws IOException {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(document);
        }
    }

    private String extractDocx(byte[] bytes) throws IOException {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes));
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return extractor.getText();
        }
    }

    private String extractDoc(byte[] bytes) throws IOException {
        try (HWPFDocument document = new HWPFDocument(new ByteArrayInputStream(bytes));
             WordExtractor extractor = new WordExtractor(document)) {
            return extractor.getText();
        }
    }

    /** Normalizes line endings and spaces, drops control characters and limits the length. */
    static String clean(String raw) {
        if (raw == null) return "";

        String text = raw.replace("\r\n", "\n").replace('\r', '\n')
            .replace(' ', ' ')
            .replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", "")
            .replaceAll("[ \\t]+", " ")
            .replaceAll(" *\\n *", "\n")
            .replaceAll("\\n{3,}", "\n\n")
            .strip();

        return text.length() > MAX_TEXT_LENGTH ? text.substring(0, MAX_TEXT_LENGTH) : text;
    }
}
