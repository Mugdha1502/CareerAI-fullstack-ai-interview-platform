import { Link } from "react-router-dom";

import {
  FiArrowRight,
  FiTarget,
  FiTrendingUp,
  FiCheckCircle,
  FiLayers,
  FiMap
} from "react-icons/fi";

import { useAuth } from "../../context/auth";
import {
  trackCategories,
  getSelectedTrack,
  resolveActiveTrack,
  getDemoSkillGap,
  getPracticeLink
} from "../career/careerTrackData";

function CareerTracks() {
  const { user } = useAuth();

  const selectedTrack = getSelectedTrack();
  const activeTrack = resolveActiveTrack(user);
  const { readiness, skills } = getDemoSkillGap(activeTrack);

  const strongSkills = skills.filter((skill) => skill.status === "Strong").map((skill) => skill.name);
  const prioritySkill = skills[skills.length - 1];
  const priorityLink = getPracticeLink(prioritySkill.name);

  const scrollToTracks = () => {
    document
      .getElementById("career-track-list")
      ?.scrollIntoView({ behavior: "smooth", block: "start" });
  };

  return (
    <div className="career-tracks-page">

      <section className="career-track-hero">

        <div className="section-container career-track-hero-grid">

          <div className="career-track-hero-content">

            <span className="section-tag">
              CAREER TRACKS
            </span>

            <h1>
              Prepare with a roadmap built around your
              <span> target role.</span>
            </h1>

            <p>
              CareerAI organizes skills, preparation topics and interview
              practice into structured role-based learning paths.
            </p>

            <div className="career-hero-actions">

              <a
                href="#career-track-list"
                className="primary-button"
              >
                Explore Career Tracks
                <FiArrowRight />
              </a>

              <button
                type="button"
                className="secondary-button"
                onClick={scrollToTracks}
              >
                <FiTarget />
                Choose My Goal
              </button>

            </div>

          </div>


          <div className="career-goal-preview">

            <div className="career-goal-header">

              <div>
                <span>{selectedTrack ? "YOUR SELECTED TRACK" : "EXAMPLE TARGET ROLE"}</span>
                <h3>{activeTrack.title}</h3>
              </div>

              <div className="career-goal-icon">
                <FiTarget />
              </div>

            </div>

            <div className="career-readiness-overview">

              <div className="career-readiness-score">
                <strong>{readiness}%</strong>
                <span>Demo readiness</span>
              </div>

              <div className="career-readiness-info">

                <div>
                  <span>Strong Skills</span>
                  <strong>{strongSkills.slice(0, 2).join(", ") || "Building foundation"}</strong>
                </div>

                <div>
                  <span>Needs Focus</span>
                  <strong>{prioritySkill.name}</strong>
                </div>

              </div>

            </div>

            <Link
              to={priorityLink ? priorityLink.path : `/career-tracks/${activeTrack.id}`}
              className="career-next-step career-next-step-link"
            >

              <FiTrendingUp />

              <div>
                <span>Recommended next</span>
                <strong>
                  {prioritySkill.name} fundamentals
                </strong>
              </div>

              <FiArrowRight />

            </Link>

            <small className="career-demo-note">
              Sample values until practice and assessment data is connected.
            </small>

          </div>

        </div>

      </section>


      <section
        className="home-section"
        id="career-track-list"
      >

        <div className="section-container">

          <div className="section-heading centered">

            <span className="section-tag">
              CHOOSE YOUR PATH
            </span>

            <h2>
              Explore CareerAI career tracks
            </h2>

            <p>
              Choose a role to open its detailed roadmap, skills and
              preparation plan.
            </p>

          </div>


          {trackCategories.map((category) => (
            <div
              className="career-category-section"
              key={category.title}
            >

              <div className="career-category-heading">
                <h3>{category.title}</h3>

                <span>
                  {category.tracks.length} tracks
                </span>
              </div>


              <div className="career-full-grid">

                {category.tracks.map((track) => {
                  const Icon = track.icon;
                  const isSelected = selectedTrack?.id === track.id;

                  return (
                    <Link
                      to={`/career-tracks/${track.id}`}
                      className={`career-full-card${isSelected ? " selected" : ""}`}
                      key={track.id}
                    >

                      <div className="career-card-top">

                        <div className="career-full-icon">
                          <Icon />
                        </div>

                        {isSelected ? (
                          <span className="career-selected-badge">
                            <FiCheckCircle />
                            Your track
                          </span>
                        ) : (
                          <span className="career-level">
                            {track.level}
                          </span>
                        )}

                      </div>

                      <h3>{track.title}</h3>

                      <p>{track.cardDescription}</p>

                      <div className="career-card-meta">
                        <span>
                          <FiLayers />
                          {track.skills.length} skills
                        </span>
                        <span>
                          <FiMap />
                          {track.focus}
                        </span>
                      </div>

                      <div className="career-card-action">
                        View Roadmap
                        <FiArrowRight />
                      </div>

                    </Link>
                  );
                })}

              </div>

            </div>
          ))}

        </div>

      </section>

    </div>
  );
}

export default CareerTracks;
