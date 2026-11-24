import { useState } from "react";
import { Link, NavLink } from "react-router-dom";
import { FiMenu, FiX, FiChevronDown } from "react-icons/fi";
import { useAuth } from "../../context/auth";

function Navbar() {
  const [menuOpen, setMenuOpen] = useState(false);
  const { isRegistered } = useAuth();

  const closeMenu = () => {
    setMenuOpen(false);
  };

  // Signed-in users go back to their dashboard; everyone else can log in or register.
  const accountLinks = isRegistered ? (
    <Link to="/dashboard" className="nav-primary-button" onClick={closeMenu}>
      Dashboard
    </Link>
  ) : (
    <>
      <Link to="/login" className="login-link" onClick={closeMenu}>
        Log in
      </Link>

      <Link to="/register" className="nav-primary-button" onClick={closeMenu}>
        Get Started
      </Link>
    </>
  );

  return (
    <header className="public-navbar">
      <div className="navbar-container">

        <Link to="/" className="brand" onClick={closeMenu}>
          <div className="brand-icon">C</div>

          <div className="brand-text">
            Career<span>AI</span>
          </div>
        </Link>

        <nav className={`navbar-links ${menuOpen ? "active" : ""}`}>

          <NavLink to="/" onClick={closeMenu}>
            Home
          </NavLink>

          <NavLink to="/features" onClick={closeMenu}>
            Features
          </NavLink>

          <NavLink to="/how-it-works" onClick={closeMenu}>
            How It Works
          </NavLink>

          <NavLink to="/ai-interview" onClick={closeMenu}>
            AI Interview
          </NavLink>

          <NavLink to="/resume-tools" onClick={closeMenu}>
            Resume Tools
          </NavLink>

          <div className="nav-dropdown">
            <span>
              Explore <FiChevronDown />
            </span>

            <div className="dropdown-menu">
              <Link to="/practice" onClick={closeMenu}>
                Practice
              </Link>

              <Link to="/career-tracks" onClick={closeMenu}>
                Career Tracks
              </Link>

              <Link to="/resources" onClick={closeMenu}>
                Resources
              </Link>
            </div>
          </div>

          {/* The desktop actions are hidden on small screens, so the open menu repeats them. */}
          <div className="navbar-mobile-actions">
            {accountLinks}
          </div>

        </nav>

        <div className="navbar-actions">
          {accountLinks}
        </div>

        <button
          className="mobile-menu-button"
          onClick={() => setMenuOpen(!menuOpen)}
          aria-label="Toggle navigation"
        >
          {menuOpen ? <FiX /> : <FiMenu />}
        </button>

      </div>
    </header>
  );
}

export default Navbar;