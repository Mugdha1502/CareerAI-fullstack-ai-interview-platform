import {
  Link,
  Outlet
} from "react-router-dom";


function AuthLayout() {

  return (
    <div className="auth-layout">

      <header className="auth-header">

        <Link
          to="/"
          className="auth-brand"
        >

          <div className="auth-brand-icon">
            C
          </div>

          <strong>
            Career<span>AI</span>
          </strong>

        </Link>


        <Link
          to="/"
          className="auth-back-link"
        >
          Back to website
        </Link>

      </header>


      <main className="auth-main">
        <Outlet />
      </main>

    </div>
  );
}


export default AuthLayout;