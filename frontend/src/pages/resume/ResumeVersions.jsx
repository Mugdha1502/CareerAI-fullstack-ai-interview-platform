import { useEffect, useState } from "react";
import { Link, useLocation } from "react-router-dom";
import {
  FiFileText,
  FiPlus,
  FiEye,
  FiEdit2,
  FiCopy,
  FiTrash2,
  FiX,
  FiCheck,
  FiArrowRight,
  FiLoader,
  FiAlertCircle
} from "react-icons/fi";
import { toApiError } from "../../services/api";
import {
  createVersion,
  deleteVersion,
  duplicateVersion,
  getVersions,
  renameVersion
} from "../../services/resumeService";
import {
  ResumeEmptyState,
  ResumeGuestNotice,
  ResumeLoadState
} from "../../components/resume/ResumePicker";
import {
  describeResume,
  formatDateTime,
  pickDefaultResume,
  useResumeList
} from "../../components/resume/useResumeList";

function versionStatus(version, latestNumber) {
  if (version.versionNumber === latestNumber) return "Latest";
  if (version.versionNumber === 1) return "Original";
  return "Previous";
}

function statusClass(status) {
  return status === "Latest" ? "current" : status.toLowerCase();
}

function ResumeVersions() {
  const { state } = useLocation();
  const { resumes, status: listStatus, error: listError, reload, upsert } = useResumeList();
  const [chosenId, setChosenId] = useState(state?.resumeId || null);
  // Versions per resume id: { status, items, error }
  const [versionsByResume, setVersionsByResume] = useState({});
  const [busy, setBusy] = useState(false);
  const [actionError, setActionError] = useState("");
  const [openId, setOpenId] = useState(null);
  const [renamingId, setRenamingId] = useState(null);
  const [renameValue, setRenameValue] = useState("");

  const selectedId = resumes.some((resume) => resume.id === chosenId)
    ? chosenId
    : pickDefaultResume(resumes)?.id ?? null;
  const selectedResume = resumes.find((resume) => resume.id === selectedId);
  const entry = selectedId ? versionsByResume[selectedId] : undefined;
  const versions = entry?.items || [];
  const latestNumber = versions.reduce((max, version) => Math.max(max, version.versionNumber), 0);
  const openVersion = versions.find((version) => version.id === openId);

  useEffect(() => {
    if (!selectedId || versionsByResume[selectedId]) return undefined;

    let cancelled = false;

    getVersions(selectedId)
      .then((items) => {
        if (!cancelled) setVersionsByResume((current) => ({ ...current, [selectedId]: { status: "ready", items, error: "" } }));
      })
      .catch((err) => {
        if (!cancelled) setVersionsByResume((current) => ({ ...current, [selectedId]: { status: "error", items: [], error: toApiError(err).message } }));
      });

    return () => {
      cancelled = true;
    };
  }, [selectedId, versionsByResume]);

  const setVersions = (updater) => {
    setVersionsByResume((current) => ({
      ...current,
      [selectedId]: { status: "ready", error: "", items: updater(current[selectedId]?.items || []) }
    }));
  };

  /** Runs one backend action with a shared busy/error state. */
  const run = async (action) => {
    setBusy(true);
    setActionError("");
    try {
      await action();
    } catch (err) {
      setActionError(toApiError(err).message);
    } finally {
      setBusy(false);
    }
  };

  const handleNewVersion = () => run(async () => {
    const version = await createVersion(selectedId, {});
    setVersions((items) => [version, ...items]);
  });

  const startRename = (version) => {
    setRenamingId(version.id);
    setRenameValue(version.versionName);
  };

  const saveRename = (event) => {
    event.preventDefault();
    const name = renameValue.trim();
    const versionId = renamingId;
    setRenamingId(null);

    if (!name) return;

    run(async () => {
      const updated = await renameVersion(selectedId, versionId, name);
      setVersions((items) => items.map((item) => (item.id === updated.id ? updated : item)));
    });
  };

  const handleDuplicate = (version) => run(async () => {
    const copy = await duplicateVersion(selectedId, version.id);
    setVersions((items) => [copy, ...items]);
  });

  const handleDelete = (version) => {
    if (!window.confirm(`Delete "${version.versionName}"? This cannot be undone.`)) {
      return;
    }

    run(async () => {
      await deleteVersion(selectedId, version.id);
      setVersions((items) => items.filter((item) => item.id !== version.id));
      if (openId === version.id) setOpenId(null);
    });
  };

  return (
    <div className="tool-page">

      <div className="section-container">

        <div className="tool-page-header-row">

          <div className="tool-page-header">

            <span className="section-tag">
              RESUME VERSIONS
            </span>

            <h1>Manage resume versions</h1>

            <p>
              Keep separate resume versions for different opportunities.
            </p>

          </div>

          {listStatus === "ready" && selectedId && (
            <button
              type="button"
              className="new-version-button"
              onClick={handleNewVersion}
              disabled={busy}
            >
              {busy ? <FiLoader className="resume-spin" /> : <FiPlus />}
              New Version
            </button>
          )}

        </div>


        {listStatus === "guest" && <ResumeGuestNotice />}

        <ResumeLoadState status={listStatus} error={listError} onRetry={reload} />

        {listStatus === "ready" && resumes.length === 0 && (
          <ResumeEmptyState onUploaded={(resume) => { upsert(resume); setChosenId(resume.id); }} />
        )}


        {listStatus === "ready" && resumes.length > 0 && (
          <>
            <div className="rv-resume-bar">

              <label className="rb-field">
                <span>Resume</span>
                <select
                  value={selectedId ?? ""}
                  onChange={(event) => {
                    setChosenId(Number(event.target.value));
                    setOpenId(null);
                    setActionError("");
                  }}
                >
                  {resumes.map((resume) => (
                    <option key={resume.id} value={resume.id}>
                      {resume.title} — {describeResume(resume)}
                    </option>
                  ))}
                </select>
              </label>

              <span className="resume-demo-badge rv-demo-badge">
                New Version saves a snapshot of the resume&apos;s current content
              </span>

            </div>

            {actionError && (
              <p className="resume-inline-error" role="alert">
                <FiAlertCircle />
                {actionError}
              </p>
            )}

            {!entry && <ResumeLoadState status="loading" label="Loading versions..." />}

            {entry?.status === "error" && (
              <ResumeLoadState
                status="error"
                error={entry.error}
                onRetry={() => setVersionsByResume((current) => {
                  const next = { ...current };
                  delete next[selectedId];
                  return next;
                })}
              />
            )}

            {entry?.status === "ready" && versions.length === 0 && (
              <div className="rv-empty">
                <FiFileText />
                <p>No versions saved for this resume. Click New Version to create one.</p>
              </div>
            )}


            <div className="resume-version-page-list">

              {versions.map((version) => {
                const status = versionStatus(version, latestNumber);

                return (
                  <div
                    className={`resume-version-page-card${status === "Latest" ? " rv-current" : ""}`}
                    key={version.id}
                  >

                    <div className="version-file-icon">
                      <FiFileText />
                    </div>

                    <div className="rv-info">

                      {renamingId === version.id ? (
                        <form
                          className="rv-rename-form"
                          onSubmit={saveRename}
                        >
                          <input
                            aria-label="Version name"
                            value={renameValue}
                            maxLength={120}
                            autoFocus
                            onChange={(event) => setRenameValue(event.target.value)}
                            onKeyDown={(event) => event.key === "Escape" && setRenamingId(null)}
                          />
                          <button
                            type="submit"
                            aria-label="Save name"
                          >
                            <FiCheck />
                          </button>
                          <button
                            type="button"
                            aria-label="Cancel rename"
                            onClick={() => setRenamingId(null)}
                          >
                            <FiX />
                          </button>
                        </form>
                      ) : (
                        <h3>{version.versionName}</h3>
                      )}

                      <div className="rv-meta">
                        <span className={`rv-status ${statusClass(status)}`}>
                          {status}
                        </span>
                        <span>Version {version.versionNumber} · {version.wordCount} words</span>
                        <span>Updated {formatDateTime(version.updatedAt)}</span>
                      </div>

                    </div>


                    <div className="rv-actions">

                      <button
                        type="button"
                        className="rv-open"
                        onClick={() => setOpenId(version.id)}
                      >
                        <FiEye />
                        Open
                      </button>

                      <button
                        type="button"
                        aria-label={`Rename ${version.versionName}`}
                        title="Rename"
                        disabled={busy}
                        onClick={() => startRename(version)}
                      >
                        <FiEdit2 />
                      </button>

                      <button
                        type="button"
                        aria-label={`Duplicate ${version.versionName}`}
                        title="Duplicate"
                        disabled={busy}
                        onClick={() => handleDuplicate(version)}
                      >
                        <FiCopy />
                      </button>

                      <button
                        type="button"
                        className="rv-delete"
                        aria-label={`Delete ${version.versionName}`}
                        title="Delete"
                        disabled={busy}
                        onClick={() => handleDelete(version)}
                      >
                        <FiTrash2 />
                      </button>

                    </div>

                  </div>
                );
              })}

            </div>
          </>
        )}

      </div>


      {openVersion && (
        <div
          className="rb-modal-backdrop"
          role="dialog"
          aria-modal="true"
          aria-label={openVersion.versionName}
          onClick={() => setOpenId(null)}
        >

          <div
            className="rb-modal rv-modal"
            onClick={(event) => event.stopPropagation()}
          >

            <div className="rb-modal-header">

              <strong>{openVersion.versionName}</strong>

              <button
                type="button"
                className="rb-icon-button"
                aria-label="Close"
                onClick={() => setOpenId(null)}
              >
                <FiX />
              </button>

            </div>

            <div className="rv-details">

              <div>
                <span>Resume</span>
                <strong>{selectedResume?.title}</strong>
              </div>

              <div>
                <span>Version</span>
                <strong>{openVersion.versionNumber} · {openVersion.wordCount} words</strong>
              </div>

              <div>
                <span>Created</span>
                <strong>{formatDateTime(openVersion.createdAt)}</strong>
              </div>

            </div>

            {openVersion.content
              ? <VersionPaper content={openVersion.content} />
              : <pre className="rv-text-preview">{openVersion.extractedText || "No text stored for this version."}</pre>}

            <div className="rv-modal-actions">

              <Link
                to="/resume/ats-analyzer"
                className="secondary-button"
              >
                Analyze Resume
              </Link>

              {openVersion.content && (
                <Link
                  to={`/resume/builder?resumeId=${selectedId}&versionId=${openVersion.id}`}
                  className="primary-button"
                >
                  Open in Builder
                  <FiArrowRight />
                </Link>
              )}

            </div>

          </div>

        </div>
      )}

    </div>
  );
}


/** Read-only preview of a builder version. */
function VersionPaper({ content }) {
  const personal = content.personal || {};
  const contact = [personal.email, personal.phone, personal.location, personal.linkedin].filter(Boolean);

  return (
    <div className="builder-resume-paper rv-paper">
      <h2>{personal.name || "Unnamed"}</h2>
      {personal.role && <p className="builder-role">{personal.role}</p>}
      {contact.length > 0 && <p className="rb-contact">{contact.join("  ·  ")}</p>}
      <hr />

      {content.summary && (
        <>
          <h4>SUMMARY</h4>
          <p className="rb-paper-text">{content.summary}</p>
        </>
      )}

      {content.education?.length > 0 && <h4>EDUCATION</h4>}
      {content.education?.map((entry, index) => (
        <div className="rb-paper-entry" key={index}>
          <strong>{entry.degree || "Degree"}</strong>
          <span>{[entry.institution, entry.year, entry.grade].filter(Boolean).join(" · ")}</span>
        </div>
      ))}

      {content.experience?.length > 0 && <h4>EXPERIENCE</h4>}
      {content.experience?.map((entry, index) => (
        <div className="rb-paper-entry" key={index}>
          <strong>{[entry.title, entry.company].filter(Boolean).join(" — ")}</strong>
          {entry.period && <span>{entry.period}</span>}
          {entry.description && <p className="rb-paper-text">{entry.description}</p>}
        </div>
      ))}

      {content.projects?.length > 0 && <h4>PROJECTS</h4>}
      {content.projects?.map((entry, index) => (
        <div className="rb-paper-entry" key={index}>
          <strong>{entry.name || "Project"}</strong>
          {entry.tech && <span>{entry.tech}</span>}
          {entry.description && <p className="rb-paper-text">{entry.description}</p>}
        </div>
      ))}

      {content.skills?.length > 0 && (
        <>
          <h4>SKILLS</h4>
          <div className="builder-skills">
            {content.skills.map((skill) => <span key={skill}>{skill}</span>)}
          </div>
        </>
      )}
    </div>
  );
}

export default ResumeVersions;
