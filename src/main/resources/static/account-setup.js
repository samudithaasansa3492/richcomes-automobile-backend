(() => {
  "use strict";

  // SMTP links contain "#purpose=...&token=...". Fragments never reach the
  // server in normal HTTP requests, and are immediately cleared from the URL.
  const fragment = new URLSearchParams(window.location.hash.slice(1));
  const purpose = fragment.get("purpose");
  const token = fragment.get("token");
  window.history.replaceState(null, "", window.location.pathname);

  const heading = document.getElementById("page-title");
  const intro = document.getElementById("intro");
  const status = document.getElementById("status");
  const passwordForm = document.getElementById("password-form");
  const confirmForm = document.getElementById("confirm-form");
  const encoder = new TextEncoder();
  const tokenValid = typeof token === "string" && /^[A-Za-z0-9_-]{43}$/.test(token);

  const flows = {
    INITIAL_PASSWORD: {title: "Set up your customer password", endpoint: "/api/auth/recovery/initial-password"},
    RESET_PASSWORD: {title: "Reset your password", endpoint: "/api/auth/recovery/reset"},
    VERIFY_EMAIL: {title: "Confirm your recovery email", endpoint: "/api/auth/recovery/email/confirm"}
  };

  const flow = Object.prototype.hasOwnProperty.call(flows, purpose) ? flows[purpose] : null;
  if (!window.isSecureContext || !tokenValid || !flow) {
    intro.textContent = "This secure link cannot be used. Ask the garage for a new link.";
    return;
  }

  heading.textContent = flow.title;
  intro.textContent = purpose === "INITIAL_PASSWORD"
    ? "Choose a password only you know. The garage owner must separately activate your account afterward."
    : purpose === "RESET_PASSWORD"
      ? "Choose a new password to replace your existing password."
      : "Confirm that this email address belongs to you.";

  const form = purpose === "VERIFY_EMAIL" ? confirmForm : passwordForm;
  const submitButton = form.querySelector("button");
  form.hidden = false;

  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    if (submitButton.disabled) return;

    let payload = {token};
    if (purpose !== "VERIFY_EMAIL") {
      const password = document.getElementById("new-password").value;
      const confirmation = document.getElementById("confirm-password").value;
      if (password.length < 15 || password.length > 72 ||
          encoder.encode(password).byteLength > 72 || password !== confirmation) {
        status.textContent = "Use matching passwords of at least 15 characters and at most 72 UTF-8 bytes.";
        return;
      }
      payload = {token, password};
    }

    submitButton.disabled = true;
    status.textContent = "Submitting your request securely.";
    try {
      const response = await fetch(flow.endpoint, {
        method: "POST",
        headers: {"Content-Type": "application/json"},
        credentials: "omit",
        cache: "no-store",
        referrerPolicy: "no-referrer",
        body: JSON.stringify(payload)
      });
      if (!response.ok) {
        status.textContent = "The request could not be completed. Your link may have expired. Ask the garage for a new link.";
      } else {
        form.hidden = true;
        status.textContent = purpose === "INITIAL_PASSWORD"
          ? "Password saved. Your customer account is still disabled until the garage owner activates it."
          : purpose === "RESET_PASSWORD"
            ? "Password updated. You can now sign in using the new password."
            : "Email confirmed. Your verified recovery email is now active.";
      }
    } catch (_error) {
      status.textContent = "The request could not be completed. Try a new secure link.";
    } finally {
      document.getElementById("new-password").value = "";
      document.getElementById("confirm-password").value = "";
      submitButton.disabled = false;
      // Passwords and bearer-like one-time tokens are not logged or persisted.
    }
  });
})();
