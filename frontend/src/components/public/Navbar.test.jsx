import { cleanup, render, screen, within } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import Navbar from "./Navbar";

/* The public header is shown on practice, resume and interview pages too, so it must follow login state. */

const auth = { isRegistered: false };
vi.mock("../../context/auth", () => ({ useAuth: () => auth }));

function renderNavbar() {
  return render(
    <MemoryRouter>
      <Navbar />
    </MemoryRouter>
  );
}

afterEach(cleanup);

describe("public Navbar account links", () => {
  it("offers Log in and Get Started to visitors and guests", () => {
    auth.isRegistered = false;
    const { container } = renderNavbar();
    const actions = container.querySelector(".navbar-actions");
    expect(within(actions).getByRole("link", { name: "Log in" }).getAttribute("href")).toBe("/login");
    expect(within(actions).getByRole("link", { name: "Get Started" }).getAttribute("href")).toBe("/register");
    expect(within(actions).queryByRole("link", { name: "Dashboard" })).toBeNull();
  });

  it("offers Dashboard instead of Log in / Get Started to signed-in users", () => {
    auth.isRegistered = true;
    renderNavbar();
    expect(screen.queryByRole("link", { name: "Log in" })).toBeNull();
    expect(screen.queryByRole("link", { name: "Get Started" })).toBeNull();
    const dashboards = screen.getAllByRole("link", { name: "Dashboard" });
    expect(dashboards.length).toBeGreaterThan(0);
    dashboards.forEach((link) => expect(link.getAttribute("href")).toBe("/dashboard"));
  });

  it.each([[false, ["Log in", "Get Started"]], [true, ["Dashboard"]]])(
    "repeats the account links inside the mobile menu (signed in: %s)",
    (isRegistered, labels) => {
      auth.isRegistered = isRegistered;
      const { container } = renderNavbar();
      const mobile = container.querySelector(".navbar-links .navbar-mobile-actions");
      expect(mobile).not.toBeNull();
      expect(within(mobile).getAllByRole("link").map((a) => a.textContent.trim())).toEqual(labels);
    }
  );
});
