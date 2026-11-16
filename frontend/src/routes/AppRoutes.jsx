import { Routes, Route } from "react-router-dom";

import PublicLayout from "../layouts/PublicLayout";
import AuthLayout from "../layouts/AuthLayout";
import UserLayout from "../layouts/UserLayout";

import Home from "../pages/public/Home";
import Features from "../pages/public/Features";
import HowItWorks from "../pages/public/HowItWorks";
import AIInterview from "../pages/public/AIInterview";
import ResumeTools from "../pages/public/ResumeTools";
import Practice from "../pages/public/Practice";
import CareerTracks from "../pages/public/CareerTracks";
import Resources from "../pages/public/Resources";
import About from "../pages/public/About";
import Contact from "../pages/public/Contact";
import FAQ from "../pages/public/FAQ";

import ResumeBuilder from "../pages/resume/ResumeBuilder";
import ATSAnalyzer from "../pages/resume/ATSAnalyzer";
import JobMatch from "../pages/resume/JobMatch";
import AIResumeImprovement from "../pages/resume/AIResumeImprovement";
import ResumeVersions from "../pages/resume/ResumeVersions";
import ResumeInterviewPreparation from "../pages/resume/ResumeInterviewPreparation";
import PracticeModule from "../pages/practice/PracticeModule";
import CareerTrackDetails from "../pages/career/CareerTracksDetails";
import CareerTopicDetails from "../pages/career/CareerTopicDetails";
import SkillGap from "../pages/career/SkillGap";
import Performance from "../pages/performance/Performance";
import MyPlan from "../pages/plan/MyPlan";
import Coach from "../pages/coach/Coach";
import Applications from "../pages/applications/Applications";
import Achievements from "../pages/achievements/Achievements";
import QuestionBank from "../pages/questions/QuestionBank";

import InterviewSetup from "../pages/interview/InterviewSetup";
import DeviceCheck from "../pages/interview/DeviceCheck";
import InterviewSession from "../pages/interview/InterviewSession";
import InterviewReport from "../pages/interview/InterviewReport";
import VoiceInterviewSession from "../pages/interview/VoiceInterviewSession";

import Login from "../pages/auth/Login";
import Register from "../pages/auth/Register";

import Dashboard from "../pages/dashboard/Dashboard";
import Profile from "../pages/profile/Profile";
import Settings from "../pages/settings/Settings";
import Pricing from "../pages/subscription/Pricing";
import Billing from "../pages/settings/Billing";
import ProtectedRoute from "../components/common/ProtectedRoute";

import Onboarding from "../pages/auth/Onboarding";

function AppRoutes() {
  return (
    <Routes>

      <Route element={<PublicLayout />}>
        <Route path="/" element={<Home />} />
        <Route
          path="/career/skill-gap"
          element={<SkillGap />}
        />

        <Route path="/features" element={<Features />} />

        <Route
          path="/question-bank"
          element={<QuestionBank />}
        />
        <Route path="/how-it-works" element={<HowItWorks />} />
        <Route path="/ai-interview" element={<AIInterview />} />

        {/* AI Interview Flow: interviews and reports are saved to the account */}
        <Route
          path="/ai-interview/setup"
          element={<ProtectedRoute><InterviewSetup /></ProtectedRoute>}
        />

        <Route
          path="/ai-interview/device-check"
          element={<ProtectedRoute><DeviceCheck /></ProtectedRoute>}
        />

        <Route
          path="/ai-interview/session"
          element={<ProtectedRoute><InterviewSession /></ProtectedRoute>}
        />

        <Route
          path="/ai-interview/report"
          element={<ProtectedRoute><InterviewReport /></ProtectedRoute>}
        />

        <Route
          path="/ai-interview/voice/:id"
          element={<ProtectedRoute><VoiceInterviewSession /></ProtectedRoute>}
        />

        <Route path="/resume-tools" element={<ResumeTools />} />
        <Route path="/practice" element={<Practice />} />
        <Route
          path="/practice/:module"
          element={<PracticeModule />}
        />

        <Route path="/career-tracks" element={<CareerTracks />} />
        <Route
          path="/career-tracks/:trackId"
          element={<CareerTrackDetails />}
        />
        <Route
          path="/career-tracks/:trackId/topic/:topicId"
          element={<CareerTopicDetails />}
        />
        <Route path="/resources" element={<Resources />} />
        <Route path="/about" element={<About />} />
        <Route path="/contact" element={<Contact />} />
        <Route path="/faq" element={<FAQ />} />

        {/* Resume Tool Subpages: data is saved to the account, so a session is required */}
        <Route path="/resume/builder" element={<ProtectedRoute><ResumeBuilder /></ProtectedRoute>} />
        <Route path="/resume/ats-analyzer" element={<ProtectedRoute><ATSAnalyzer /></ProtectedRoute>} />
        <Route path="/resume/job-match" element={<ProtectedRoute><JobMatch /></ProtectedRoute>} />
        <Route
          path="/resume/ai-improvement"
          element={<ProtectedRoute><AIResumeImprovement /></ProtectedRoute>}
        />
        <Route
          path="/resume/versions"
          element={<ProtectedRoute><ResumeVersions /></ProtectedRoute>}
        />
        <Route
          path="/resume/interview-preparation"
          element={<ProtectedRoute><ResumeInterviewPreparation /></ProtectedRoute>}
        />

      </Route>

      <Route element={<AuthLayout />}>

        <Route
          path="/login"
          element={<Login />}
        />

        <Route
          path="/register"
          element={<Register />}
        />

        <Route
          path="/onboarding"
          element={
            <ProtectedRoute>
              <Onboarding />
            </ProtectedRoute>
          }
        />

      </Route>


      <Route
        element={
          <ProtectedRoute>
            <UserLayout />
          </ProtectedRoute>
        }
      >

        <Route
          path="/dashboard"
          element={<Dashboard />}
        />

        <Route path="/my-plan" element={<MyPlan />} />
        <Route path="/performance" element={<Performance />} />
        <Route path="/achievements" element={<Achievements />} />
        <Route path="/applications" element={<Applications />} />
        <Route path="/coach" element={<Coach />} />
        <Route path="/profile" element={<Profile />} />
        <Route path="/settings" element={<Settings />} />
        <Route path="/pricing" element={<Pricing />} />
        <Route path="/settings/billing" element={<Billing />} />

      </Route>


    </Routes>
  );
}

export default AppRoutes;