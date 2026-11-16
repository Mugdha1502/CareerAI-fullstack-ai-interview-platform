import { useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";

import {
  FiArrowLeft,
  FiArrowRight,
  FiBookOpen,
  FiTarget,
  FiCheckCircle,
  FiClock,
  FiAward,
  FiCode,
  FiBriefcase,
  FiMic,
  FiMap,
  FiLayers,
  FiFolder,
  FiMessageSquare,
  FiBarChart2,
  FiCalendar,
  FiCompass
} from "react-icons/fi";

import { useAuth } from "../../context/AuthContext";
import {
  getTrack,
  getSelectedTrack,
  saveSelectedTrack,
  getPracticeLink,
  findTopicForSkill
} from "./careerTrackData";

// A skill or focus item links to practice first, then to a roadmap topic, otherwise nowhere.
function resolveSkillLink(track, label) {
  const practice = getPracticeLink(label);
  if (practice) return { path: practice.path, hint: practice.label };

  const topic = findTopicForSkill(track, label);
  if (topic) return { path: `/career-tracks/${track.id}/topic/${topic.id}`, hint: `Open ${topic.title}` };

  return null;
}

function CareerTrackDetails() {
  const { trackId } = useParams();
  const track = getTrack(trackId);

  if (!track) {
    return (
      <div className="tool-page">

        <div className="section-container">

          <h1>Career track not found</h1>

          <Link
            to="/career-tracks"
            className="primary-button"
          >
            Return to Career Tracks
          </Link>

        </div>

      </div>
    );
  }

  // key resets selection state when moving between tracks.
  return (
    <CareerTrackView
      key={track.id}
      track={track}
    />
  );
}


function CareerTrackView({ track }) {
  const navigate = useNavigate();
  const { user, saveProfile } = useAuth();

  const [isSelected, setIsSelected] = useState(
    () => getSelectedTrack()?.id === track.id || user?.profile?.targetRole === track.title
  );

  const Icon = track.icon;
  const firstTechnical = track.practiceTopics
    .map((topic) => getPracticeLink(topic, topic))
    .find((link) => link?.module === "technical");

  const [choosing, setChoosing] = useState(false);

  const chooseTrack = async () => {
    if (choosing) return;

    saveSelectedTrack(track);

    // Save the target role to the profile (PUT /api/profile/me; guests: local session)
    // so My Plan and Skill Gap follow the choice after a refresh.
    if (user) {
      setChoosing(true);

      try {
        await saveProfile({
          ...(user.profile || {}),
          targetRole: track.title
        });
      } catch {
        // The choice is still kept locally in careerai_selected_track.
      }
    }

    setIsSelected(true);
    navigate("/my-plan");
  };

  const actions = [
    {
      icon: <FiBookOpen />,
      title: "Technical Practice",
      text: firstTechnical
        ? `Start with ${firstTechnical.topic}, a core topic for this role.`
        : "Practise technical questions relevant to this career path.",
      to: firstTechnical ? firstTechnical.path : "/practice/technical",
      cta: "Start Practice"
    },
    {
      icon: <FiCode />,
      title: "Coding Practice",
      text: "Solve coding problems and strengthen problem solving.",
      to: "/practice/coding",
      cta: "Start Coding"
    },
    {
      icon: <FiMic />,
      title: "AI Interview",
      text: `Practise a role-based interview for ${track.title}.`,
      to: "/ai-interview/setup?type=role",
      state: { source: "Role-Based", targetRole: track.title },
      cta: "Set Up Interview"
    },
    {
      icon: <FiBriefcase />,
      title: "Resume Tools",
      text: "Tailor your resume for this career direction.",
      to: "/resume-tools",
      cta: "Open Resume Tools"
    },
    {
      icon: <FiBarChart2 />,
      title: "View Skill Gap",
      text: "See which skills for this role need the most work.",
      to: `/career/skill-gap?track=${track.id}`,
      cta: "View Skill Gap"
    },
    {
      icon: <FiCalendar />,
      title: "Build My Plan",
      text: "Turn this roadmap into a daily preparation plan.",
      to: "/my-plan",
      cta: "Build My Plan"
    }
  ];

  return (
    <div className="career-detail-page">

      <section className="career-detail-hero">

        <div className="section-container">

          <Link
            to="/career-tracks"
            className="page-back-link"
          >
            <FiArrowLeft />
            All Career Tracks
          </Link>


          <div className="career-detail-heading">

            <div className="career-detail-title">

              <div className="career-detail-hero-icon">
                <Icon />
              </div>

              <div>

                <span className="section-tag">
                  CAREER TRACK · {track.category.toUpperCase()}
                </span>

                <h1>{track.title}</h1>

                <p>{track.description}</p>

                <div className="career-hero-chips">
                  <span><FiClock />{track.level}</span>
                  <span><FiLayers />{track.skills.length} skills</span>
                  <span><FiMap />{track.roadmap.length} roadmap topics</span>
                  <span><FiCompass />{track.focus}</span>
                </div>

              </div>

            </div>

            <div className="career-choose">

              <button
                type="button"
                className={`primary-button career-choose-button${isSelected ? " selected" : ""}`}
                onClick={chooseTrack}
                disabled={choosing}
                aria-busy={choosing}
              >
                {isSelected ? (
                  <>
                    <FiCheckCircle />
                    Track Selected ✓
                  </>
                ) : (
                  <>
                    Choose This Track
                    <FiArrowRight />
                  </>
                )}
              </button>

              <small>
                {isSelected
                  ? "Saved as your target role. Click to open My Plan."
                  : "Sets this as your target role and opens My Plan."}
              </small>

            </div>

          </div>

        </div>

      </section>


      {/* ROLE OVERVIEW */}

      <section className="career-overview-section">

        <div className="section-container career-overview-grid">

          <div className="career-overview-card">

            <div className="career-detail-section-header">
              <FiTarget />
              <div>
                <span>ROLE OVERVIEW</span>
                <h2>What this role involves</h2>
              </div>
            </div>

            <p>{track.overview}</p>

          </div>

          <div className="career-overview-stats">

            <div className="career-overview-stat">
              <FiClock />
              <span>Preparation Level</span>
              <strong>{track.level}</strong>
            </div>

            <div className="career-overview-stat">
              <FiAward />
              <span>Skills Covered</span>
              <strong>{track.skills.length}</strong>
            </div>

            <div className="career-overview-stat">
              <FiMap />
              <span>Roadmap Topics</span>
              <strong>{track.roadmap.length}</strong>
            </div>

            <div className="career-overview-stat">
              <FiFolder />
              <span>Projects</span>
              <strong>{track.recommendedProjects.length}</strong>
            </div>

          </div>

        </div>

      </section>


      {/* ROADMAP + SKILLS */}

      <section className="home-section">

        <div className="section-container career-detail-grid">

          <div className="career-detail-roadmap">

            <div className="career-detail-section-header">

              <FiBookOpen />

              <div>
                <span>LEARNING ROADMAP</span>
                <h2>Career Roadmap</h2>
              </div>

            </div>


            <ol className="career-detail-roadmap-list">

              {track.roadmap.map((item, index) => (
                <li key={item.id}>

                  <Link
                    to={`/career-tracks/${track.id}/topic/${item.id}`}
                    className="career-detail-step"
                  >

                    <div className="career-detail-step-number">
                      {index + 1}
                    </div>

                    <div className="career-detail-step-text">
                      <strong>{item.title}</strong>
                      <span>{item.purpose}</span>
                    </div>

                    <span className="career-step-open">
                      Open Topic
                      <FiArrowRight />
                    </span>

                  </Link>

                </li>
              ))}

            </ol>

          </div>


          <aside className="career-detail-sidebar">

            <div className="career-detail-side-card">

              <div className="career-detail-section-header">

                <FiTarget />

                <div>
                  <span>SKILLS YOU NEED</span>
                  <h3>Important Skills</h3>
                </div>

              </div>


              <div className="career-detail-skills">

                {track.skills.map((skill) => {
                  const link = resolveSkillLink(track, skill);

                  return link ? (
                    <Link
                      key={skill}
                      to={link.path}
                      className="career-skill-chip linked"
                      title={link.hint}
                    >
                      <FiCheckCircle />
                      {skill}
                      <FiArrowRight className="career-skill-arrow" />
                    </Link>
                  ) : (
                    <span
                      key={skill}
                      className="career-skill-chip"
                    >
                      <FiCheckCircle />
                      {skill}
                    </span>
                  );
                })}

              </div>

              <p className="career-skill-legend">
                Linked skills open practice or a roadmap topic.
              </p>

            </div>


            <div className="career-detail-side-card">

              <div className="career-meta-row">
                <FiClock />

                <div>
                  <span>Preparation Level</span>
                  <strong>{track.level}</strong>
                </div>
              </div>

              <div className="career-meta-row">
                <FiAward />

                <div>
                  <span>Skills Covered</span>
                  <strong>{track.skills.length}</strong>
                </div>
              </div>

            </div>

          </aside>

        </div>

      </section>


      {/* RECOMMENDED PROJECTS */}

      <section className="home-section career-projects-section">

        <div className="section-container">

          <div className="section-heading">
            <span className="section-tag">
              RECOMMENDED PROJECTS
            </span>
            <h2>Projects that prove you can do this job</h2>
            <p>Build one or two of these and be ready to explain every design decision.</p>
          </div>

          <div className="career-project-grid">

            {track.recommendedProjects.map((item) => (
              <article
                key={item.title}
                className="career-project-card"
              >

                <div className="career-project-head">
                  <FiFolder />
                  <span className={`career-project-difficulty ${item.difficulty.toLowerCase()}`}>
                    {item.difficulty}
                  </span>
                </div>

                <h3>{item.title}</h3>

                <p className="career-project-tech">
                  {item.tech.join(" • ")}
                </p>

                <div className="career-project-demo">
                  <span>What it demonstrates</span>
                  <p>{item.demonstrates}</p>
                </div>

              </article>
            ))}

          </div>

        </div>

      </section>


      {/* INTERVIEW FOCUS */}

      <section className="home-section career-interview-section">

        <div className="section-container">

          <div className="career-interview-card">

            <div className="career-detail-section-header">
              <FiMessageSquare />
              <div>
                <span>INTERVIEW FOCUS</span>
                <h2>What interviewers usually test</h2>
              </div>
            </div>

            <div className="career-interview-list">

              {track.interviewFocus.map((item, index) => {
                const link = resolveSkillLink(track, item);

                const content = (
                  <>
                    <span className="career-interview-index">
                      {String(index + 1).padStart(2, "0")}
                    </span>
                    <strong>{item}</strong>
                    {link && <FiArrowRight className="career-interview-arrow" />}
                  </>
                );

                return link ? (
                  <Link
                    key={item}
                    to={link.path}
                    className="career-interview-item linked"
                    title={link.hint}
                  >
                    {content}
                  </Link>
                ) : (
                  <div
                    key={item}
                    className="career-interview-item"
                  >
                    {content}
                  </div>
                );
              })}

            </div>

          </div>

        </div>

      </section>


      {/* PREPARATION ACTIONS */}

      <section className="home-section career-track-actions-section">

        <div className="section-container">

          <div className="section-heading centered">

            <span className="section-tag">
              PREPARATION ACTIONS
            </span>

            <h2>
              Continue your preparation inside CareerAI
            </h2>

          </div>


          <div className="career-action-grid">

            {actions.map((action) => (
              <Link
                key={action.title}
                to={action.to}
                state={action.state}
                className="career-action-card"
              >
                {action.icon}

                <h3>{action.title}</h3>

                <p>{action.text}</p>

                <span>
                  {action.cta}
                  <FiArrowRight />
                </span>
              </Link>
            ))}

          </div>

        </div>

      </section>

    </div>
  );
}

export default CareerTrackDetails;
