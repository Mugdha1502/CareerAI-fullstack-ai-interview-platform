import { useEffect, useRef, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import {
  FiUser,
  FiAlignLeft,
  FiBookOpen,
  FiBriefcase,
  FiCode,
  FiAward,
  FiPlus,
  FiDownload,
  FiEye,
  FiSave,
  FiEdit3,
  FiX,
  FiCheck,
  FiTrash2,
  FiLoader,
  FiAlertCircle,
  FiLayers
} from "react-icons/fi";
import { toApiError } from "../../services/api";
import {
  createResume,
  createVersion,
  getResume,
  getResumes,
  getVersions,
  updateResume
} from "../../services/resumeService";
import { useAuth } from "../../context/AuthContext";
import { ResumeGuestNotice } from "../../components/resume/ResumePicker";

/* ---------- Form shape <-> stored JSON shape ----------
   Stored (backend): { personal, summary, education[], experience[], projects[], skills[], certifications[] }
   Form (this page): same sections, with summary as { text } and skills as comma-separated text. */

const EMPTY_ENTRIES = {
  education: { degree: "", institution: "", year: "", grade: "" },
  experience: { title: "", company: "", period: "", description: "" },
  projects: { name: "", tech: "", description: "" }
};

function emptyForm(user) {
  return {
    personal: {
      name: user?.name || "",
      role: user?.profile?.targetRole || "",
      email: user?.email || "",
      phone: "",
      location: "",
      linkedin: ""
    },
    summary: { text: "" },
    education: [],
    experience: [],
    projects: [],
    skills: { skills: "", certifications: "" }
  };
}

function fromContent(content, user) {
  if (!content) return emptyForm(user);

  const personal = content.personal || {};

  return {
    personal: {
      name: personal.name || "",
      role: personal.role || "",
      email: personal.email || "",
      phone: personal.phone || "",
      location: personal.location || "",
      linkedin: personal.linkedin || ""
    },
    summary: { text: content.summary || "" },
    education: (content.education || []).map((entry) => ({ ...EMPTY_ENTRIES.education, ...stripNulls(entry) })),
    experience: (content.experience || []).map((entry) => ({ ...EMPTY_ENTRIES.experience, ...stripNulls(entry) })),
    projects: (content.projects || []).map((entry) => ({ ...EMPTY_ENTRIES.projects, ...stripNulls(entry) })),
    skills: {
      skills: (content.skills || []).join(", "),
      certifications: (content.certifications || []).join(", ")
    }
  };
}

function toContent(form) {
  return {
    personal: form.personal,
    summary: form.summary.text,
    education: form.education,
    experience: form.experience,
    projects: form.projects,
    skills: splitList(form.skills.skills),
    certifications: splitList(form.skills.certifications)
  };
}

function stripNulls(entry) {
  return Object.fromEntries(Object.entries(entry || {}).map(([key, value]) => [key, value ?? ""]));
}

const sections = [
  {
    id: "personal",
    title: "Personal Information",
    icon: <FiUser />,
    fields: [
      { key: "name", label: "Full name", max: 100 },
      { key: "role", label: "Target role", max: 120 },
      { key: "email", label: "Email", type: "email", max: 255 },
      { key: "phone", label: "Phone", max: 40 },
      { key: "location", label: "Location", max: 120 },
      { key: "linkedin", label: "LinkedIn / portfolio", max: 255 }
    ]
  },
  {
    id: "summary",
    title: "Summary",
    icon: <FiAlignLeft />,
    fields: [
      { key: "text", label: "Professional summary", type: "textarea", max: 3000 }
    ]
  },
  {
    id: "education",
    title: "Education",
    icon: <FiBookOpen />,
    repeatable: true,
    entryLabel: "education entry",
    fields: [
      { key: "degree", label: "Degree", max: 150 },
      { key: "institution", label: "Institution", max: 150 },
      { key: "year", label: "Graduation year", max: 40 },
      { key: "grade", label: "Grade / CGPA", max: 40 }
    ]
  },
  {
    id: "experience",
    title: "Experience",
    icon: <FiBriefcase />,
    repeatable: true,
    entryLabel: "experience entry",
    fields: [
      { key: "title", label: "Job title", max: 120 },
      { key: "company", label: "Company", max: 120 },
      { key: "period", label: "Period", max: 60 },
      { key: "description", label: "Responsibilities", type: "textarea", max: 3000 }
    ]
  },
  {
    id: "projects",
    title: "Projects",
    icon: <FiCode />,
    repeatable: true,
    entryLabel: "project",
    fields: [
      { key: "name", label: "Project name", max: 150 },
      { key: "tech", label: "Technologies", max: 200 },
      { key: "description", label: "Description", type: "textarea", max: 3000 }
    ]
  },
  {
    id: "skills",
    title: "Skills & Certifications",
    icon: <FiAward />,
    fields: [
      { key: "skills", label: "Skills (comma separated)", type: "textarea", max: 4000 },
      { key: "certifications", label: "Certifications (comma separated)", max: 4000 }
    ]
  }
];

function splitList(value) {
  return (value || "")
    .split(",")
    .map((item) => item.trim())
    .filter(Boolean);
}

function getSectionSummary(id, data) {
  if (Array.isArray(data)) {
    if (data.length === 0) return "";
    const first = getSectionSummary(id, data[0]);
    return data.length > 1 ? `${first} (+${data.length - 1} more)` : first;
  }

  switch (id) {
    case "personal":
      return [data.name, data.role].filter(Boolean).join(" · ");
    case "summary":
      return data.text;
    case "education":
      return [data.degree, data.institution].filter(Boolean).join(" · ");
    case "experience":
      return [data.title, data.company].filter(Boolean).join(" · ");
    case "projects":
      return [data.name, data.tech].filter(Boolean).join(" · ");
    case "skills":
      return data.skills;
    default:
      return "";
  }
}

const SAVE_LABELS = {
  idle: "",
  dirty: "Unsaved changes",
  saving: "Saving...",
  saved: "All changes saved",
  error: "Save failed"
};

function ResumeBuilder() {
  const { user, isAuthenticated } = useAuth();
  const [searchParams, setSearchParams] = useSearchParams();
  const requestedId = Number(searchParams.get("resumeId")) || null;
  const requestedVersionId = Number(searchParams.get("versionId")) || null;

  const [resume, setResume] = useState(() => emptyForm(user));
  const [resumeId, setResumeId] = useState(null);
  const [title, setTitle] = useState("My Resume");
  const [builderResumes, setBuilderResumes] = useState([]);
  const [loadState, setLoadState] = useState(isAuthenticated ? "loading" : "ready");
  const [loadError, setLoadError] = useState("");
  const [saveState, setSaveState] = useState("idle");
  const [saveError, setSaveError] = useState("");

  const [editingId, setEditingId] = useState(null);
  const [draft, setDraft] = useState({});
  const [isPreviewOpen, setIsPreviewOpen] = useState(false);
  const [notice, setNotice] = useState("");
  const noticeTimer = useRef(null);

  const showNotice = (message) => {
    setNotice(message);
    clearTimeout(noticeTimer.current);
    noticeTimer.current = setTimeout(() => setNotice(""), 3200);
  };

  // The primary/most recent resume is opened automatically only on the first load;
  // afterwards an empty URL means "+ New resume".
  const firstLoad = useRef(true);

  // Load the user's builder resumes, then the requested (or default) one.
  useEffect(() => {
    if (!isAuthenticated) return undefined;

    let cancelled = false;
    const pickDefault = firstLoad.current;

    async function load() {
      const list = (await getResumes()).filter((item) => item.sourceType === "BUILDER");
      const fallbackId = pickDefault ? (list.find((item) => item.primary) || list[0])?.id : null;
      const targetId = requestedId && list.some((item) => item.id === requestedId)
        ? requestedId
        : fallbackId;

      let detail = null;
      let loadedVersion = null;

      if (targetId) {
        detail = await getResume(targetId);
        if (requestedVersionId && targetId === requestedId) {
          loadedVersion = (await getVersions(targetId)).find((version) => version.id === requestedVersionId) || null;
        }
      }

      return { list, detail, loadedVersion };
    }

    load()
      .then(({ list, detail, loadedVersion }) => {
        if (cancelled) return;
        firstLoad.current = false;
        setBuilderResumes(list);
        if (detail) {
          setResumeId(detail.id);
          setTitle(detail.title);
          setResume(fromContent(loadedVersion ? loadedVersion.content : detail.content, user));
          setSaveState(loadedVersion ? "dirty" : "saved");
          if (loadedVersion) {
            showNotice(`Loaded "${loadedVersion.versionName}". Click Save to make it the current content.`);
          }
        }
        setLoadState("ready");
      })
      .catch((err) => {
        if (cancelled) return;
        setLoadError(toApiError(err).message);
        setLoadState("error");
      });

    return () => {
      cancelled = true;
    };
    // Reload only when the signed-in state or the requested resume/version changes.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isAuthenticated, requestedId, requestedVersionId]);

  const markDirty = () => {
    setSaveState("dirty");
    setSaveError("");
  };

  const startEditing = (id) => {
    setEditingId(id);
    const value = resume[id];
    setDraft(Array.isArray(value) ? value.map((entry) => ({ ...entry })) : { ...value });
  };

  const cancelEditing = () => {
    setEditingId(null);
    setDraft({});
  };

  const saveSection = (event) => {
    event.preventDefault();

    setResume((current) => ({ ...current, [editingId]: draft }));
    setEditingId(null);
    markDirty();
    showNotice("Section updated. Click Save to store your changes.");
  };

  /** Creates the resume on first save, then updates it. Returns the saved id. */
  const persist = async () => {
    const cleanTitle = title.trim() || "My Resume";
    setSaveState("saving");
    setSaveError("");

    try {
      const content = toContent(resume);
      const saved = resumeId
        ? await updateResume(resumeId, { title: cleanTitle, content })
        : await createResume({ title: cleanTitle, content });

      setResumeId(saved.id);
      setTitle(saved.title);
      setResume(fromContent(saved.content, user));
      setBuilderResumes((current) => [saved, ...current.filter((item) => item.id !== saved.id)]);
      setSaveState("saved");

      if (!resumeId || requestedVersionId) {
        setSearchParams({ resumeId: String(saved.id) }, { replace: true });
      }

      return saved.id;
    } catch (err) {
      setSaveError(toApiError(err).message);
      setSaveState("error");
      return null;
    }
  };

  const handleSave = async () => {
    const id = await persist();
    if (id) showNotice(resumeId ? "Resume saved." : "Resume created and saved to your account.");
  };

  const handleSaveVersion = async () => {
    const name = window.prompt("Name this version (optional), e.g. \"Java Developer Version\"", "");
    if (name === null) return;

    const id = saveState === "saved" && resumeId ? resumeId : await persist();
    if (!id) return;

    try {
      const version = await createVersion(id, { versionName: name.trim() || null });
      showNotice(`Saved as "${version.versionName}".`);
    } catch (err) {
      setSaveError(toApiError(err).message);
      setSaveState("error");
    }
  };

  const handleSwitchResume = (value) => {
    if (saveState === "dirty" && !window.confirm("You have unsaved changes. Discard them?")) {
      return;
    }

    cancelEditing();

    if (value === "new") {
      setResumeId(null);
      setTitle("My Resume");
      setResume(emptyForm(user));
      setSaveState("idle");
      setSearchParams({}, { replace: true });
      return;
    }

    setSearchParams({ resumeId: value });
  };

  const canSave = isAuthenticated && loadState === "ready" && saveState !== "saving" && editingId === null;

  return (
    <div className="tool-page">

      <div className="section-container">

        <div className="tool-page-header">

          <span className="section-tag">
            RESUME BUILDER
          </span>

          <h1>Create your resume</h1>

          <p>
            Build a structured ATS-friendly resume using your actual
            education, skills, projects and experience.
          </p>

        </div>


        {!isAuthenticated && <ResumeGuestNotice />}


        {isAuthenticated && (
          <div className="rb-resume-bar">

            <label className="rb-field">
              <span>Resume</span>
              <select
                value={resumeId ? String(resumeId) : "new"}
                onChange={(event) => handleSwitchResume(event.target.value)}
                disabled={loadState !== "ready" || saveState === "saving"}
              >
                <option value="new">+ New resume</option>
                {builderResumes.map((item) => (
                  <option key={item.id} value={item.id}>
                    {item.title}{item.primary ? " (primary)" : ""}
                  </option>
                ))}
              </select>
            </label>

            <label className="rb-field">
              <span>Title</span>
              <input
                value={title}
                maxLength={120}
                onChange={(event) => {
                  setTitle(event.target.value);
                  markDirty();
                }}
              />
            </label>

            {resumeId && (
              <Link to="/resume/versions" state={{ resumeId }} className="rb-versions-link">
                <FiLayers />
                Versions
              </Link>
            )}

          </div>
        )}


        <div className="rb-toolbar">

          <span className={`resume-demo-badge rb-save-state ${saveState}`} role="status">
            {!isAuthenticated && "Guest session · sign in to save"}
            {isAuthenticated && loadState === "loading" && "Loading your resumes..."}
            {isAuthenticated && loadState === "error" && loadError}
            {isAuthenticated && loadState === "ready" && (SAVE_LABELS[saveState] || (resumeId ? "Saved to your account" : "New resume · not saved yet"))}
          </span>

          <div className="rb-toolbar-actions">

            <button
              type="button"
              className="rb-action-button"
              onClick={() => setIsPreviewOpen(true)}
            >
              <FiEye />
              Preview
            </button>

            <button
              type="button"
              className="rb-action-button"
              onClick={() => showNotice("PDF export will be connected later.")}
            >
              <FiDownload />
              Download
            </button>

            <button
              type="button"
              className="rb-action-button"
              onClick={handleSaveVersion}
              disabled={!canSave}
            >
              <FiLayers />
              Save Version
            </button>

            <button
              type="button"
              className="rb-action-button primary"
              onClick={handleSave}
              disabled={!canSave}
            >
              {saveState === "saving" ? <FiLoader className="resume-spin" /> : <FiSave />}
              {resumeId ? "Save" : "Create Resume"}
            </button>

          </div>

        </div>

        {saveError && (
          <p className="resume-inline-error" role="alert">
            <FiAlertCircle />
            {saveError}
          </p>
        )}


        <div className="resume-builder-layout">

          <div className="resume-builder-form">

            {sections.map((section) => {
              const isEditing = editingId === section.id;

              return (
                <div
                  key={section.id}
                  className={`resume-builder-section${isEditing ? " rb-editing" : ""}`}
                >

                  <div className="rb-section-head">

                    <div className="builder-section-title">
                      {section.icon}
                      <h3>{section.title}</h3>
                    </div>

                    {!isEditing && (
                      <button
                        type="button"
                        className="rb-edit-button"
                        onClick={() => startEditing(section.id)}
                        disabled={editingId !== null}
                      >
                        <FiEdit3 />
                        Edit
                      </button>
                    )}

                  </div>


                  {!isEditing && (
                    <p className="rb-section-summary">
                      {getSectionSummary(section.id, resume[section.id]) ||
                        `Add your ${section.title.toLowerCase()} details.`}
                    </p>
                  )}


                  {isEditing && (
                    <form
                      className="rb-editor"
                      onSubmit={saveSection}
                    >

                      {section.repeatable ? (
                        <RepeatableEditor
                          section={section}
                          entries={draft}
                          onChange={setDraft}
                        />
                      ) : (
                        <FieldGrid
                          fields={section.fields}
                          value={draft}
                          onChange={setDraft}
                        />
                      )}

                      <div className="rb-editor-actions">

                        <button
                          type="button"
                          className="rb-action-button"
                          onClick={cancelEditing}
                        >
                          <FiX />
                          Cancel
                        </button>

                        <button
                          type="submit"
                          className="rb-action-button primary"
                        >
                          <FiCheck />
                          Done
                        </button>

                      </div>

                    </form>
                  )}

                </div>
              );
            })}

            <button
              type="button"
              className="resume-add-section"
              onClick={() => showNotice("Custom sections will be available in a later version.")}
            >
              <FiPlus />
              Add Section
            </button>

          </div>


          <div className="builder-preview-card">

            <div className="builder-preview-header">

              <span>LIVE PREVIEW</span>

              <button
                type="button"
                onClick={() => setIsPreviewOpen(true)}
              >
                <FiEye />
                Full preview
              </button>

            </div>

            <ResumePaper resume={resume} />

          </div>

        </div>

      </div>


      {isPreviewOpen && (
        <div
          className="rb-modal-backdrop"
          role="dialog"
          aria-modal="true"
          aria-label="Resume preview"
          onClick={() => setIsPreviewOpen(false)}
        >

          <div
            className="rb-modal"
            onClick={(event) => event.stopPropagation()}
          >

            <div className="rb-modal-header">

              <strong>Resume preview</strong>

              <button
                type="button"
                className="rb-icon-button"
                aria-label="Close preview"
                onClick={() => setIsPreviewOpen(false)}
              >
                <FiX />
              </button>

            </div>

            <ResumePaper resume={resume} />

          </div>

        </div>
      )}


      {notice && (
        <div
          className="resume-toast"
          role="status"
        >
          {notice}
        </div>
      )}

    </div>
  );
}


function FieldGrid({ fields, value, onChange }) {
  return (
    <div className="rb-field-grid">

      {fields.map((field) => (
        <label
          key={field.key}
          className={`rb-field${field.type === "textarea" ? " full" : ""}`}
        >
          <span>{field.label}</span>

          {field.type === "textarea" ? (
            <textarea
              rows={4}
              maxLength={field.max}
              value={value[field.key] || ""}
              onChange={(event) =>
                onChange({ ...value, [field.key]: event.target.value })
              }
            />
          ) : (
            <input
              type={field.type || "text"}
              maxLength={field.max}
              value={value[field.key] || ""}
              onChange={(event) =>
                onChange({ ...value, [field.key]: event.target.value })
              }
            />
          )}
        </label>
      ))}

    </div>
  );
}


function RepeatableEditor({ section, entries, onChange }) {
  const list = Array.isArray(entries) ? entries : [];

  const updateEntry = (index, value) => {
    onChange(list.map((entry, i) => (i === index ? value : entry)));
  };

  return (
    <div className="rb-entries">

      {list.length === 0 && (
        <p className="rb-section-summary">No {section.entryLabel} added yet.</p>
      )}

      {list.map((entry, index) => (
        <fieldset key={index} className="rb-entry">

          <legend>
            {section.title} {index + 1}
            <button
              type="button"
              className="rb-entry-remove"
              aria-label={`Remove ${section.entryLabel} ${index + 1}`}
              onClick={() => onChange(list.filter((_, i) => i !== index))}
            >
              <FiTrash2 />
            </button>
          </legend>

          <FieldGrid
            fields={section.fields}
            value={entry}
            onChange={(value) => updateEntry(index, value)}
          />

        </fieldset>
      ))}

      {list.length < 20 && (
        <button
          type="button"
          className="rb-action-button rb-add-entry"
          onClick={() => onChange([...list, { ...EMPTY_ENTRIES[section.id] }])}
        >
          <FiPlus />
          Add {section.entryLabel}
        </button>
      )}

    </div>
  );
}


function ResumePaper({ resume }) {
  const { personal, summary, education, experience, projects, skills } = resume;

  const contact = [
    personal.email,
    personal.phone,
    personal.location,
    personal.linkedin
  ].filter(Boolean);

  const skillList = splitList(skills.skills);
  const certificationList = splitList(skills.certifications);

  return (
    <div className="builder-resume-paper rb-paper">

      <h2>{personal.name || "Your Name"}</h2>

      <p className="builder-role">
        {personal.role || "Target Role"}
      </p>

      {contact.length > 0 && (
        <p className="rb-contact">
          {contact.join("  ·  ")}
        </p>
      )}

      <hr />

      <h4>SUMMARY</h4>
      <p className="rb-paper-text">
        {summary.text || "Add a short professional summary."}
      </p>

      <h4>EDUCATION</h4>
      {education.length === 0 && <p className="rb-paper-text">Add your education.</p>}
      {education.map((entry, index) => (
        <div className="rb-paper-entry" key={index}>
          <strong>{entry.degree || "Degree"}</strong>
          <span>{[entry.institution, entry.year].filter(Boolean).join(" · ")}</span>
          {entry.grade && <span>{entry.grade}</span>}
        </div>
      ))}

      <h4>EXPERIENCE</h4>
      {experience.length === 0 && <p className="rb-paper-text">Add internships or work experience, if any.</p>}
      {experience.map((entry, index) => (
        <div className="rb-paper-entry" key={index}>
          <strong>
            {[entry.title, entry.company].filter(Boolean).join(" — ") || "Role — Company"}
          </strong>
          {entry.period && <span>{entry.period}</span>}
          {entry.description && <p className="rb-paper-text">{entry.description}</p>}
        </div>
      ))}

      <h4>PROJECTS</h4>
      {projects.length === 0 && <p className="rb-paper-text">Add projects you have built.</p>}
      {projects.map((entry, index) => (
        <div className="rb-paper-entry" key={index}>
          <strong>{entry.name || "Project name"}</strong>
          {entry.tech && <span>{entry.tech}</span>}
          {entry.description && <p className="rb-paper-text">{entry.description}</p>}
        </div>
      ))}

      <h4>SKILLS</h4>
      <div className="builder-skills">
        {skillList.length > 0
          ? skillList.map((skill) => <span key={skill}>{skill}</span>)
          : <span>Add skills</span>}
      </div>

      {certificationList.length > 0 && (
        <>
          <h4>CERTIFICATIONS</h4>
          <p className="rb-paper-text">
            {certificationList.join(", ")}
          </p>
        </>
      )}

    </div>
  );
}

export default ResumeBuilder;
