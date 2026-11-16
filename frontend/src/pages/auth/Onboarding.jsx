import {
  useState
} from "react";

import {
  useNavigate
} from "react-router-dom";

import {
  FiUser,
  FiBookOpen,
  FiTarget,
  FiClock,
  FiArrowLeft,
  FiArrowRight,
  FiLoader
} from "react-icons/fi";

import {
  useAuth
} from "../../context/AuthContext";

import {
  addXP,
  XP_REWARDS
} from "../../services/xpService";

import {
  toApiError
} from "../../services/api";

import {
  splitSkills
} from "../../services/profileService";


function Onboarding() {

  const navigate =
    useNavigate();


  const {
    user,
    completeOnboarding
  } = useAuth();


  const [step, setStep] =
    useState(1);


  // Pre-fill from a saved profile when onboarding is revisited.
  const [form, setForm] =
    useState(() => ({

      status:
        user?.profile?.status || "",

      education:
        user?.profile?.education || "",

      experience:
        user?.profile?.experience || "",

      skills:
        user?.profile?.skills || "",

      targetRole:
        user?.profile?.targetRole || "",

      goal:
        user?.profile?.goal ||
        "Interview Preparation",

      dailyTime:
        user?.profile?.dailyTime || "60",

      interviewDifficulty:
        user?.profile?.interviewDifficulty ||
        "Beginner"

    }));


  const [stepError,
    setStepError] =
    useState("");

  const [submitError,
    setSubmitError] =
    useState("");

  const [submitting,
    setSubmitting] =
    useState(false);


  const update = (
    field,
    value
  ) => {

    setForm(
      (previous) => ({

        ...previous,

        [field]: value

      })
    );

    setStepError("");
    setSubmitError("");

  };


  // Same rules as the backend OnboardingRequest, checked per step.
  const validateStep = (
    current
  ) => {

    if (current === 1 && !form.status) {
      return "Please choose where you are currently.";
    }

    if (current === 2) {

      if (!form.experience) {
        return "Please choose your experience level.";
      }

      if (form.education.trim().length > 150) {
        return "Education must be at most 150 characters.";
      }

      const skills =
        splitSkills(form.skills);

      if (skills.length > 30) {
        return "Add at most 30 skills.";
      }

      if (skills.some((skill) => skill.length > 60)) {
        return "Each skill must be at most 60 characters.";
      }
    }

    if (current === 3 && !form.targetRole) {
      return "Please choose a target role.";
    }

    if (current === 4) {

      if (!form.goal) {
        return "Please choose a main goal.";
      }

      if (!form.dailyTime) {
        return "Please choose your daily preparation time.";
      }

      if (!form.interviewDifficulty) {
        return "Please choose a preferred difficulty.";
      }
    }

    return "";
  };


  const next = () => {

    const message =
      validateStep(step);

    setStepError(message);

    if (!message && step < 4) {
      setStep(
        step + 1
      );
    }

  };


  const previous = () => {

    setStepError("");
    setSubmitError("");

    if (step > 1) {
      setStep(
        step - 1
      );
    }

  };


  const finish = async () => {

    if (submitting) {
      return;
    }

    // Re-check every step in case earlier answers were cleared.
    for (const current of [1, 2, 3, 4]) {

      const message =
        validateStep(current);

      if (message) {
        setStep(current);
        setStepError(message);
        return;
      }
    }


    const firstCompletion =
      !user?.onboardingCompleted;


    setSubmitting(true);
    setSubmitError("");

    try {

      // POST /api/profile/onboarding, then refresh /auth/me.
      await completeOnboarding(
        form
      );


      // Award the profile XP once, after the backend confirmed the save.
      if (firstCompletion) {

        addXP(
          user.id,
          XP_REWARDS.PROFILE_COMPLETE,
          "PROFILE_COMPLETE",
          "Completed CareerAI onboarding"
        );

      }


      navigate(
        "/dashboard",
        { replace: true }
      );

    } catch (error) {

      const apiError =
        toApiError(error);

      setSubmitError(
        apiError.status === 0
          ? "Unable to connect to CareerAI server."
          : apiError.message
      );

      setSubmitting(false);
    }

  };


  return (
    <div className="onboarding-page">

      <div className="onboarding-wrapper">

        <div className="onboarding-progress">

          {[1, 2, 3, 4].map(
            (number) => (

              <div
                key={number}
                className={
                  number <= step
                    ? "active"
                    : ""
                }
              ></div>

            )
          )}

        </div>


        <div className="onboarding-card">

          {step === 1 && (

            <>

              <OnboardingHeading
                icon={<FiUser />}
                step={step}
                title="Where are you currently?"
                text="This helps CareerAI understand your starting point."
              />


              <div className="onboarding-options">

                {[
                  "Student",
                  "Fresher",
                  "Working Professional",
                  "Career Switcher"
                ].map(
                  (option) => (

                    <button
                      type="button"
                      aria-pressed={form.status === option}
                      key={option}
                      className={
                        form.status ===
                        option
                          ? "selected"
                          : ""
                      }
                      onClick={() =>
                        update(
                          "status",
                          option
                        )
                      }
                    >
                      {option}
                    </button>

                  )
                )}

              </div>

            </>

          )}


          {step === 2 && (

            <>

              <OnboardingHeading
                icon={<FiBookOpen />}
                step={step}
                title="Education and skills"
                text="Give CareerAI some basic background about you."
              />


              <div className="onboarding-form">

                <div>

                  <label>
                    Education
                  </label>

                  <input
                    placeholder="e.g. B.Tech Computer Science"
                    value={
                      form.education
                    }
                    onChange={(event) =>
                      update(
                        "education",
                        event.target.value
                      )
                    }
                  />

                </div>


                <div>

                  <label>
                    Experience
                  </label>

                  <select
                    value={
                      form.experience
                    }
                    onChange={(event) =>
                      update(
                        "experience",
                        event.target.value
                      )
                    }
                  >
                    <option value="">
                      Select experience
                    </option>

                    <option>
                      No professional experience
                    </option>

                    <option>
                      Less than 1 year
                    </option>

                    <option>
                      1-3 years
                    </option>

                    <option>
                      3-5 years
                    </option>

                    <option>
                      5+ years
                    </option>

                  </select>

                </div>


                <div>

                  <label>
                    Current skills
                  </label>

                  <textarea
                    placeholder="Java, SQL, React, Python..."
                    value={
                      form.skills
                    }
                    onChange={(event) =>
                      update(
                        "skills",
                        event.target.value
                      )
                    }
                  />

                </div>

              </div>

            </>

          )}


          {step === 3 && (

            <>

              <OnboardingHeading
                icon={<FiTarget />}
                step={step}
                title="Choose your target role"
                text="Your roadmap and recommendations will use this career goal."
              />


              <div className="onboarding-options role-options">

                {[
                  "Java Full Stack Developer",
                  "Frontend Developer",
                  "Backend Developer",
                  "Software Engineer",
                  "Python Developer",
                  "Data Analyst",
                  "Data Scientist",
                  "Machine Learning Engineer",
                  "AI Engineer",
                  "DevOps Engineer",
                  "Cloud Engineer",
                  "Cybersecurity Analyst"
                ].map(
                  (role) => (

                    <button
                      type="button"
                      aria-pressed={form.targetRole === role}
                      key={role}
                      className={
                        form.targetRole ===
                        role
                          ? "selected"
                          : ""
                      }
                      onClick={() =>
                        update(
                          "targetRole",
                          role
                        )
                      }
                    >
                      {role}
                    </button>

                  )
                )}

              </div>

            </>

          )}


          {step === 4 && (

            <>

              <OnboardingHeading
                icon={<FiClock />}
                step={step}
                title="Set your preparation goal"
                text="CareerAI will use this when creating your daily plan."
              />


              <div className="onboarding-form">

                <div>

                  <label>
                    Main goal
                  </label>

                  <select
                    value={
                      form.goal
                    }
                    onChange={(event) =>
                      update(
                        "goal",
                        event.target.value
                      )
                    }
                  >

                    <option>
                      Interview Preparation
                    </option>

                    <option>
                      Placement Preparation
                    </option>

                    <option>
                      Improve Technical Skills
                    </option>

                    <option>
                      Career Switch
                    </option>

                    <option>
                      Job Search
                    </option>

                  </select>

                </div>


                <div>

                  <label>
                    Daily preparation time
                  </label>

                  <select
                    value={
                      form.dailyTime
                    }
                    onChange={(event) =>
                      update(
                        "dailyTime",
                        event.target.value
                      )
                    }
                  >

                    <option value="30">
                      30 minutes
                    </option>

                    <option value="60">
                      1 hour
                    </option>

                    <option value="90">
                      1.5 hours
                    </option>

                    <option value="120">
                      2 hours
                    </option>

                  </select>

                </div>


                <div>

                  <label htmlFor="onboarding-difficulty">
                    Preferred difficulty
                  </label>

                  <select
                    id="onboarding-difficulty"
                    value={
                      form.interviewDifficulty
                    }
                    onChange={(event) =>
                      update(
                        "interviewDifficulty",
                        event.target.value
                      )
                    }
                  >

                    <option>
                      Beginner
                    </option>

                    <option>
                      Intermediate
                    </option>

                    <option>
                      Advanced
                    </option>

                  </select>

                </div>

              </div>

            </>

          )}


          {(stepError || submitError) && (

            <div
              className="auth-error onboarding-error"
              role="alert"
            >
              {stepError || submitError}
            </div>

          )}


          <div className="onboarding-actions">

            {step > 1 ? (

              <button
                type="button"
                className="secondary-button"
                onClick={previous}
                disabled={submitting}
              >
                <FiArrowLeft />
                Back
              </button>

            ) : (

              <span></span>

            )}


            {step < 4 ? (

              <button
                type="button"
                className="primary-button"
                onClick={next}
              >
                Continue
                <FiArrowRight />
              </button>

            ) : (

              <button
                type="button"
                className="primary-button"
                onClick={finish}
                disabled={submitting}
                aria-busy={submitting}
              >
                {submitting ? (
                  <>
                    <FiLoader className="auth-spinner" aria-hidden="true" />
                    Saving…
                  </>
                ) : (
                  <>
                    Open Dashboard
                    <FiArrowRight />
                  </>
                )}
              </button>

            )}

          </div>

        </div>

      </div>

    </div>
  );
}


function OnboardingHeading({
  icon,
  step,
  title,
  text
}) {

  return (
    <div className="onboarding-heading">

      <span className="section-tag">
        STEP {step} OF 4
      </span>

      {icon}

      <h1>{title}</h1>

      <p>{text}</p>

    </div>
  );
}


export default Onboarding;