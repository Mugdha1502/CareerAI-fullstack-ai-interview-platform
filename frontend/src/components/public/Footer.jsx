import { Link } from "react-router-dom";
import {
  FiGithub,
  FiLinkedin,
  FiMail,
  FiArrowRight
} from "react-icons/fi";

function Footer() {
  return (
    <footer className="public-footer">

      <div className="footer-container">

        <div className="footer-main">

          <div className="footer-brand-section">

            <Link to="/" className="brand footer-brand">
              <div className="brand-icon">C</div>

              <div className="brand-text">
                Career<span>AI</span>
              </div>
            </Link>

            <p>
              Prepare smarter for your career with personalized learning,
              interview practice, resume tools and AI-powered guidance.
            </p>

            <div className="footer-socials">
              <a href="#" aria-label="LinkedIn">
                <FiLinkedin />
              </a>

              <a href="#" aria-label="GitHub">
                <FiGithub />
              </a>

              <a href="mailto:support@careerai.com" aria-label="Email">
                <FiMail />
              </a>
            </div>

          </div>

          <div className="footer-column">
            <h4>Platform</h4>

            <Link to="/features">Features</Link>
            <Link to="/ai-interview">AI Interview</Link>
            <Link to="/resume-tools">Resume Tools</Link>
            <Link to="/practice">Practice</Link>
            <Link to="/career-tracks">Career Tracks</Link>
          </div>

          <div className="footer-column">
            <h4>Resources</h4>

            <Link to="/resources">Career Resources</Link>
            <Link to="/how-it-works">How It Works</Link>
            <Link to="/faq">FAQ</Link>
          </div>

          <div className="footer-column">
            <h4>Company</h4>

            <Link to="/about">About</Link>
            <Link to="/contact">Contact</Link>
          </div>

        </div>

        <div className="footer-newsletter">

          <div>
            <h3>Build a better preparation routine</h3>
            <p>
              Create your CareerAI account and start preparing for your
              next opportunity.
            </p>
          </div>

          <Link to="/register" className="footer-cta">
            Get Started <FiArrowRight />
          </Link>

        </div>

        <div className="footer-bottom">

          <p>
            © {new Date().getFullYear()} CareerAI. All rights reserved.
          </p>

          <div>
            <a href="#">Privacy</a>
            <a href="#">Terms</a>
          </div>

        </div>

      </div>

    </footer>
  );
}

export default Footer;