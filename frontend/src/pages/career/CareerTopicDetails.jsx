import { Link, useParams } from "react-router-dom";

import {
  FiArrowLeft,
  FiArrowRight,
  FiBookOpen,
  FiTarget,
  FiKey,
  FiMessageSquare,
  FiAlertTriangle,
  FiList,
  FiPlay,
  FiHelpCircle,
  FiMic,
  FiMap,
  FiCheckCircle,
  FiCode,
  FiDatabase,
  FiLayers
} from "react-icons/fi";

import {
  getTopic,
  getTopicPracticeLinks,
  buildTopicContent
} from "./careerTrackData";

const moduleIcons = {
  technical: <FiPlay />,
  sql: <FiDatabase />,
  coding: <FiCode />,
  "system-design": <FiLayers />,
  voice: <FiMic />
};

function CareerTopicDetails() {
  const { trackId, topicId } = useParams();
  const { track, topic, index } = getTopic(trackId, topicId);

  if (!track || !topic) {
    return (
      <div className="tool-page">
        <div className="section-container">
          <h1>Topic not found</h1>
          <p>This topic is not part of the selected career roadmap.</p>
          <Link
            to={track ? `/career-tracks/${track.id}` : "/career-tracks"}
            className="primary-button"
          >
            {track ? "Back to Roadmap" : "Return to Career Tracks"}
          </Link>
        </div>
      </div>
    );
  }

  const content = buildTopicContent(track, topic);
  const practice = getTopicPracticeLinks(topic);
  const previous = track.roadmap[index - 1];
  const next = track.roadmap[index + 1];
  const roadmapPath = `/career-tracks/${track.id}`;
  const progress = Math.round(((index + 1) / track.roadmap.length) * 100);

  const sections = [
    { id: "know", icon: <FiBookOpen />, title: "What you should know", body: <p>{content.know}</p> },
    { id: "why", icon: <FiTarget />, title: "Why it matters for this role", body: <p>{content.why}</p> },
    {
      id: "concepts",
      icon: <FiKey />,
      title: "Key concepts",
      body: (
        <div className="topic-concept-grid">
          {content.concepts.map((concept) => (
            <span key={concept}>
              <FiCheckCircle />
              {concept}
            </span>
          ))}
        </div>
      )
    },
    {
      id: "interview",
      icon: <FiMessageSquare />,
      title: "Interview focus",
      body: (
        <ul className="topic-question-list">
          {content.interview.map((question) => (
            <li key={question}>{question}</li>
          ))}
        </ul>
      )
    },
    {
      id: "mistakes",
      icon: <FiAlertTriangle />,
      title: "Common mistakes",
      body: (
        <ul className="topic-mistake-list">
          {content.mistakes.map((mistake) => (
            <li key={mistake}>{mistake}</li>
          ))}
        </ul>
      )
    },
    {
      id: "path",
      icon: <FiList />,
      title: "Suggested learning path",
      body: (
        <ol className="topic-path-list">
          {content.path.map((item, stepIndex) => (
            <li key={item}>
              <span>{stepIndex + 1}</span>
              {item}
            </li>
          ))}
        </ol>
      )
    }
  ];

  return (
    <div className="career-topic-page">

      <section className="career-topic-hero">

        <div className="section-container">

          <Link
            to={roadmapPath}
            className="page-back-link"
          >
            <FiArrowLeft />
            Back to {track.title} roadmap
          </Link>

          <div className="career-topic-heading">

            <div className="career-topic-labels">
              <div>
                <span>Career Track</span>
                <Link to={roadmapPath}>{track.title}</Link>
              </div>
              <div>
                <span>Topic</span>
                <strong>{topic.title}</strong>
              </div>
            </div>

            <h1>{topic.title}</h1>

            <p>{topic.purpose}</p>

            <div className="career-topic-progress">
              <div>
                <span>Roadmap step {index + 1} of {track.roadmap.length}</span>
                <strong>{progress}%</strong>
              </div>
              <div className="career-topic-progress-bar">
                <div style={{ width: `${progress}%` }}></div>
              </div>
            </div>

          </div>

        </div>

      </section>


      <section className="home-section career-topic-body">

        <div className="section-container career-topic-layout">

          <div className="career-topic-main">

            <span className="career-topic-demo">
              Demo learning content · detailed lessons will come from the CareerAI content service
            </span>

            {sections.map((section) => (
              <article
                key={section.id}
                className={`career-topic-section topic-${section.id}`}
              >
                <div className="career-topic-section-head">
                  {section.icon}
                  <h2>{section.title}</h2>
                </div>
                {section.body}
              </article>
            ))}

            <nav
              className="career-topic-pager"
              aria-label="Roadmap topics"
            >
              {previous ? (
                <Link to={`/career-tracks/${track.id}/topic/${previous.id}`}>
                  <FiArrowLeft />
                  <span>
                    <small>Previous topic</small>
                    {previous.title}
                  </span>
                </Link>
              ) : <span />}

              {next ? (
                <Link
                  to={`/career-tracks/${track.id}/topic/${next.id}`}
                  className="next"
                >
                  <span>
                    <small>Next topic</small>
                    {next.title}
                  </span>
                  <FiArrowRight />
                </Link>
              ) : (
                <Link
                  to={roadmapPath}
                  className="next"
                >
                  <span>
                    <small>Roadmap complete</small>
                    Back to Roadmap
                  </span>
                  <FiMap />
                </Link>
              )}
            </nav>

          </div>


          <aside className="career-topic-sidebar">

            <div className="career-topic-actions">

              <h3>Practise this topic</h3>

              {practice?.module === "technical" && (
                <>
                  <Link to={practice.notes} className="topic-action">
                    <FiBookOpen />
                    <span>
                      <strong>Study Notes</strong>
                      <small>{practice.topic} notes</small>
                    </span>
                  </Link>

                  <Link to={practice.path} className="topic-action primary">
                    <FiPlay />
                    <span>
                      <strong>Start Practice</strong>
                      <small>{practice.topic} · choose a mode</small>
                    </span>
                  </Link>

                  <Link to={practice.quiz} className="topic-action">
                    <FiHelpCircle />
                    <span>
                      <strong>Take Quiz</strong>
                      <small>{practice.topic} multiple choice</small>
                    </span>
                  </Link>
                </>
              )}

              {practice && practice.module !== "technical" && (
                <Link to={practice.path} className="topic-action primary">
                  {moduleIcons[practice.module]}
                  <span>
                    <strong>Start Practice</strong>
                    <small>{practice.label}</small>
                  </span>
                </Link>
              )}

              {!practice && (
                <p className="topic-action-note">
                  Dedicated practice for this topic will be added with the CareerAI question bank.
                  <Link to="/practice">Browse practice modules</Link>
                </p>
              )}

              <Link
                to="/ai-interview/setup?type=role"
                state={{ source: "Role-Based", targetRole: track.title }}
                className="topic-action"
              >
                <FiMic />
                <span>
                  <strong>Practice Interview</strong>
                  <small>Role-based · {track.title}</small>
                </span>
              </Link>

              <Link to={roadmapPath} className="topic-action subtle">
                <FiMap />
                <span>
                  <strong>Back to Roadmap</strong>
                  <small>{track.roadmap.length} topics</small>
                </span>
              </Link>

            </div>


            <div className="career-topic-roadmap">

              <h3>Roadmap</h3>

              <ol>
                {track.roadmap.map((item, itemIndex) => (
                  <li key={item.id}>
                    <Link
                      to={`/career-tracks/${track.id}/topic/${item.id}`}
                      className={item.id === topic.id ? "current" : ""}
                      aria-current={item.id === topic.id ? "page" : undefined}
                    >
                      <span>{itemIndex + 1}</span>
                      {item.title}
                    </Link>
                  </li>
                ))}
              </ol>

            </div>

          </aside>

        </div>

      </section>

    </div>
  );
}

export default CareerTopicDetails;
