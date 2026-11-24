import {
  Navigate,
  useLocation
} from "react-router-dom";

import {
  useAuth
} from "../../context/auth";


/**
 * Renders children only for a user validated through GET /auth/me
 * (or an explicit local guest session). A stored token alone is never
 * enough: while it is being checked a loading state is shown.
 */
function ProtectedRoute({
  children
}) {

  const {
    hasSession,
    loading
  } = useAuth();


  const location =
    useLocation();


  if (loading) {

    return (
      <div
        className="route-loading"
        role="status"
      >
        Loading CareerAI...
      </div>
    );

  }


  if (!hasSession) {

    return (
      <Navigate
        to="/login"
        replace
        state={{
          from:
            location.pathname +
            location.search
        }}
      />
    );

  }


  return children;
}


export default ProtectedRoute;
