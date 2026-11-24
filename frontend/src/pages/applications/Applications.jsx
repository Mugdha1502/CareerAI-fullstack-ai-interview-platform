import { useState } from "react";
import { Link } from "react-router-dom";

import {
  FiPlus,
  FiSearch,
  FiEdit2,
  FiTrash2,
  FiChevronDown,
  FiChevronUp,
  FiMic,
  FiFileText,
  FiBriefcase,
  FiCalendar,
  FiX,
  FiSave,
  FiAlertCircle,
  FiSend,
  FiAward,
  FiInfo
} from "react-icons/fi";

import { useAuth } from "../../context/auth";

/* =====================================================
   APPLICATION TRACKER (frontend demo)
   TODO backend: /api/applications CRUD will replace localStorage.
===================================================== */

const APPLICATIONS_KEY = "careerai_applications";

const APPLICATION_STATUSES = ["Saved", "Applied", "Assessment", "Interview", "Offer", "Rejected", "Withdrawn"];
const SOURCES = ["LinkedIn", "Company Website", "Naukri", "Indeed", "Referral", "Campus Placement", "Other"];

const emptyForm = {
  company: "",
  role: "",
  source: "",
  appliedDate: "",
  status: "Saved",
  jobDescription: "",
  notes: ""
};

function readApplications(userId) {
  try {
    const all = JSON.parse(localStorage.getItem(APPLICATIONS_KEY) || "{}");
    return Array.isArray(all?.[userId]) ? all[userId] : [];
  } catch {
    return [];
  }
}

function writeApplications(userId, items) {
  try {
    const all = JSON.parse(localStorage.getItem(APPLICATIONS_KEY) || "{}") || {};
    all[userId] = items;
    localStorage.setItem(APPLICATIONS_KEY, JSON.stringify(all));
  } catch {
    // Storage unavailable — changes last for this page view only.
  }
}

const statusClass = (status) => status.toLowerCase();

const formatDate = (value) =>
  value
    ? new Date(`${value}T00:00:00`).toLocaleDateString(undefined, { day: "numeric", month: "short", year: "numeric" })
    : "Not applied yet";


function Applications() {
  const { user } = useAuth();

  const [items, setItems] = useState(() => readApplications(user?.id));
  const [formOpen, setFormOpen] = useState(false);
  const [editingId, setEditingId] = useState(null);
  const [form, setForm] = useState(emptyForm);
  const [errors, setErrors] = useState({});
  const [expandedId, setExpandedId] = useState(null);
  const [search, setSearch] = useState("");
  const [statusFilter, setStatusFilter] = useState("All");

  const persist = (next) => {
    setItems(next);
    writeApplications(user?.id, next);
  };

  const openAdd = () => {
    setEditingId(null);
    setForm({ ...emptyForm, appliedDate: "" });
    setErrors({});
    setFormOpen(true);
  };

  const openEdit = (item) => {
    setEditingId(item.id);
    setForm({ ...emptyForm, ...item });
    setErrors({});
    setFormOpen(true);
    window.scrollTo({ top: 0, behavior: "smooth" });
  };

  const closeForm = () => {
    setFormOpen(false);
    setEditingId(null);
    setErrors({});
  };

  const update = (key) => (event) => {
    setForm((current) => ({ ...current, [key]: event.target.value }));
    if (errors[key]) setErrors((current) => ({ ...current, [key]: undefined }));
  };

  const submit = (event) => {
    event.preventDefault();

    const nextErrors = {};
    if (!form.company.trim()) nextErrors.company = "Company is required.";
    if (!form.role.trim()) nextErrors.role = "Role is required.";

    if (Object.keys(nextErrors).length) {
      setErrors(nextErrors);
      document.getElementById(`jobapp-${Object.keys(nextErrors)[0]}`)?.focus();
      return;
    }

    const now = new Date().toISOString();
    const cleaned = {
      ...form,
      company: form.company.trim(),
      role: form.role.trim(),
      jobDescription: form.jobDescription.trim(),
      notes: form.notes.trim()
    };

    if (editingId) {
      persist(items.map((item) => (item.id === editingId ? { ...item, ...cleaned, updatedAt: now } : item)));
    } else {
      const created = { ...cleaned, id: `app_${Date.now()}`, createdAt: now, updatedAt: now };
      persist([created, ...items]);
      setExpandedId(created.id);
    }

    closeForm();
  };

  const changeStatus = (id, status) => {
    persist(items.map((item) => (item.id === id ? { ...item, status, updatedAt: new Date().toISOString() } : item)));
  };

  const remove = (item) => {
    if (!window.confirm(`Delete the ${item.role} application at ${item.company}?`)) return;

    persist(items.filter((entry) => entry.id !== item.id));
    if (expandedId === item.id) setExpandedId(null);
    if (editingId === item.id) closeForm();
  };

  const query = search.trim().toLowerCase();
  const visible = items.filter((item) =>
    (statusFilter === "All" || item.status === statusFilter) &&
    (!query || `${item.company} ${item.role} ${item.source}`.toLowerCase().includes(query))
  );

  const count = (statuses) => items.filter((item) => statuses.includes(item.status)).length;

  const stats = [
    { label: "Total", value: items.length, icon: <FiBriefcase /> },
    { label: "Active", value: count(["Applied", "Assessment", "Interview"]), icon: <FiSend /> },
    { label: "Interviews", value: count(["Interview"]), icon: <FiMic /> },
    { label: "Offers", value: count(["Offer"]), icon: <FiAward /> }
  ];

  return (
    <div className="ua-page">

      <header className="ua-page-header">
        <div>
          <span className="ua-eyebrow">APPLICATIONS</span>
          <h1>Application tracker</h1>
          <p>Track every application and jump straight into interview or resume preparation for it.</p>
        </div>

        {!formOpen && (
          <div className="ua-header-actions">
            <button
              type="button"
              className="ua-button primary"
              onClick={openAdd}
            >
              <FiPlus aria-hidden="true" />
              Add Application
            </button>
          </div>
        )}
      </header>


      <section className="ua-stat-grid">
        {stats.map((stat) => (
          <div key={stat.label} className="ua-stat">
            <span className="ua-stat-icon" aria-hidden="true">{stat.icon}</span>
            <span className="ua-stat-label">{stat.label}</span>
            <strong className="ua-stat-value">{stat.value}</strong>
          </div>
        ))}
      </section>


      {/* ADD / EDIT FORM */}

      {formOpen && (
        <section className="ua-card jobapp-form-card">

          <div className="ua-card-header">
            <div>
              <span className="ua-card-kicker">{editingId ? "EDIT APPLICATION" : "NEW APPLICATION"}</span>
              <h2>{editingId ? `${form.role || "Role"} · ${form.company || "Company"}` : "Add an application"}</h2>
            </div>
            <button
              type="button"
              className="ua-icon-button"
              onClick={closeForm}
              aria-label="Close form"
            >
              <FiX aria-hidden="true" />
            </button>
          </div>

          <form onSubmit={submit} noValidate>

            <div className="ua-form-grid">

              <Field id="jobapp-company" label="Company" error={errors.company}>
                <input id="jobapp-company" type="text" value={form.company} onChange={update("company")}
                  aria-invalid={Boolean(errors.company)} aria-describedby={errors.company ? "jobapp-company-error" : undefined} />
              </Field>

              <Field id="jobapp-role" label="Role" error={errors.role}>
                <input id="jobapp-role" type="text" value={form.role} onChange={update("role")}
                  aria-invalid={Boolean(errors.role)} aria-describedby={errors.role ? "jobapp-role-error" : undefined} />
              </Field>

              <Field id="jobapp-source" label="Job Source">
                <select id="jobapp-source" value={form.source} onChange={update("source")}>
                  <option value="">Select source</option>
                  {SOURCES.map((source) => <option key={source}>{source}</option>)}
                </select>
              </Field>

              <Field id="jobapp-date" label="Applied Date">
                <input id="jobapp-date" type="date" value={form.appliedDate} onChange={update("appliedDate")} />
              </Field>

              <Field id="jobapp-status" label="Status">
                <select id="jobapp-status" value={form.status} onChange={update("status")}>
                  {APPLICATION_STATUSES.map((status) => <option key={status}>{status}</option>)}
                </select>
              </Field>

            </div>

            <Field id="jobapp-jd" label="Job Description" hint="Paste the job description to use it for interview preparation and resume matching.">
              <textarea id="jobapp-jd" rows={5} value={form.jobDescription} onChange={update("jobDescription")} aria-describedby="jobapp-jd-hint" />
            </Field>

            <Field id="jobapp-notes" label="Notes">
              <textarea id="jobapp-notes" rows={3} value={form.notes} onChange={update("notes")} placeholder="Recruiter name, next steps, salary range…" />
            </Field>

            <div className="ua-form-actions">
              <button type="button" className="ua-button ghost" onClick={closeForm}>Cancel</button>
              <button type="submit" className="ua-button primary">
                <FiSave aria-hidden="true" />
                {editingId ? "Save Changes" : "Add Application"}
              </button>
            </div>

          </form>

        </section>
      )}


      {/* LIST */}

      <section className="ua-card">

        <div className="jobapp-toolbar">

          <div className="jobapp-search">
            <FiSearch aria-hidden="true" />
            <label htmlFor="jobapp-search" className="sr-only">Search applications</label>
            <input
              id="jobapp-search"
              type="search"
              placeholder="Search company or role"
              value={search}
              onChange={(event) => setSearch(event.target.value)}
            />
          </div>

          <div className="jobapp-filter">
            <label htmlFor="jobapp-filter">Status</label>
            <select
              id="jobapp-filter"
              value={statusFilter}
              onChange={(event) => setStatusFilter(event.target.value)}
            >
              <option>All</option>
              {APPLICATION_STATUSES.map((status) => (
                <option key={status} value={status}>{status} ({count([status])})</option>
              ))}
            </select>
          </div>

        </div>

        {items.length === 0 ? (
          <div className="ua-empty">
            <FiBriefcase aria-hidden="true" />
            <p>No applications yet. Add the jobs you're applying for to keep track of each stage.</p>
            {!formOpen && (
              <button type="button" className="ua-button primary" onClick={openAdd}>
                <FiPlus aria-hidden="true" />
                Add your first application
              </button>
            )}
          </div>
        ) : visible.length === 0 ? (
          <div className="ua-empty">
            <p>No applications match your search or filter.</p>
            <button
              type="button"
              className="ua-button ghost"
              onClick={() => { setSearch(""); setStatusFilter("All"); }}
            >
              Clear filters
            </button>
          </div>
        ) : (
          <ul className="jobapp-list">
            {visible.map((item) => {
              const expanded = expandedId === item.id;
              const interviewState = { source: "Job-Based", targetRole: item.role, jobDescription: item.jobDescription };
              const detailsId = `jobapp-details-${item.id}`;

              return (
                <li key={item.id} className={`jobapp-item${expanded ? " expanded" : ""}`}>

                  <div className="jobapp-row">

                    <span className="jobapp-logo" aria-hidden="true">
                      {item.company.charAt(0).toUpperCase()}
                    </span>

                    <div className="jobapp-main">
                      <strong>{item.role}</strong>
                      <span>{item.company}</span>
                      <small>
                        <FiCalendar aria-hidden="true" />
                        {formatDate(item.appliedDate)}
                        {item.source && ` · ${item.source}`}
                      </small>
                    </div>

                    <div className="jobapp-status-wrap">
                      <label htmlFor={`status-${item.id}`} className="sr-only">
                        Status for {item.role} at {item.company}
                      </label>
                      <select
                        id={`status-${item.id}`}
                        className={`jobapp-status ${statusClass(item.status)}`}
                        value={item.status}
                        onChange={(event) => changeStatus(item.id, event.target.value)}
                      >
                        {APPLICATION_STATUSES.map((status) => <option key={status}>{status}</option>)}
                      </select>
                    </div>

                    <div className="jobapp-row-actions">
                      <button
                        type="button"
                        className="ua-button small ghost"
                        aria-expanded={expanded}
                        aria-controls={detailsId}
                        onClick={() => setExpandedId(expanded ? null : item.id)}
                      >
                        {expanded ? <FiChevronUp aria-hidden="true" /> : <FiChevronDown aria-hidden="true" />}
                        Details
                      </button>
                      <button
                        type="button"
                        className="ua-icon-button"
                        onClick={() => openEdit(item)}
                        aria-label={`Edit ${item.role} at ${item.company}`}
                      >
                        <FiEdit2 aria-hidden="true" />
                      </button>
                      <button
                        type="button"
                        className="ua-icon-button danger"
                        onClick={() => remove(item)}
                        aria-label={`Delete ${item.role} at ${item.company}`}
                      >
                        <FiTrash2 aria-hidden="true" />
                      </button>
                    </div>

                  </div>

                  {expanded && (
                    <div id={detailsId} className="jobapp-details">

                      <div className="jobapp-details-grid">
                        <div>
                          <h3>Job Description</h3>
                          {item.jobDescription
                            ? <p className="jobapp-pre">{item.jobDescription}</p>
                            : <p className="ua-muted">No job description added. <button type="button" className="ua-text-button" onClick={() => openEdit(item)}>Add one</button> for better interview and resume matching.</p>}
                        </div>
                        <div>
                          <h3>Notes</h3>
                          {item.notes ? <p className="jobapp-pre">{item.notes}</p> : <p className="ua-muted">No notes.</p>}
                        </div>
                      </div>

                      <div className="jobapp-details-actions">
                        <Link
                          to="/ai-interview/setup?type=job"
                          state={interviewState}
                          className="ua-button primary small"
                        >
                          <FiMic aria-hidden="true" />
                          Prepare for Interview
                        </Link>
                        <Link
                          to="/resume/job-match"
                          state={{ jobDescription: item.jobDescription, company: item.company, role: item.role }}
                          className="ua-button ghost small"
                        >
                          <FiFileText aria-hidden="true" />
                          Match Resume
                        </Link>
                        <small>
                          Last updated {new Date(item.updatedAt).toLocaleDateString()}
                        </small>
                      </div>

                    </div>
                  )}

                </li>
              );
            })}
          </ul>
        )}

        <p className="ua-footnote">
          <FiInfo aria-hidden="true" />
          Applications are saved on this device until account sync is connected.
        </p>

      </section>

    </div>
  );
}


function Field({ id, label, hint, error, children }) {
  return (
    <div className={`ua-field${error ? " has-error" : ""}`}>
      <label htmlFor={id}>{label}</label>
      {children}
      {hint && <small id={`${id}-hint`}>{hint}</small>}
      {error && (
        <span id={`${id}-error`} className="ua-field-error">
          <FiAlertCircle aria-hidden="true" />
          {error}
        </span>
      )}
    </div>
  );
}

export default Applications;
