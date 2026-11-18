import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import {runInNewContext} from "node:vm";
import {TextEncoder} from "node:util";

const script = readFileSync("src/main/resources/static/account-setup.js", "utf8");
const html = readFileSync("src/main/resources/static/account-setup.html", "utf8");

function simulate(purpose, token = "A".repeat(43), secure = true) {
  const events = {};
  const calls = [];
  const submittedButton = {disabled: false};
  const nodes = {
    "page-title": {textContent: ""},
    intro: {textContent: ""},
    status: {textContent: ""},
    "password-form": {hidden: true, querySelector: () => submittedButton,
      addEventListener: (_, handler) => {events.password = handler;}},
    "confirm-form": {hidden: true, querySelector: () => submittedButton,
      addEventListener: (_, handler) => {events.confirm = handler;}},
    "new-password": {value: "customer only secret password"},
    "confirm-password": {value: "customer only secret password"}
  };
  const replaced = [];
  const context = {
    window: {isSecureContext: secure,
      location: {hash: "#purpose=" + purpose + "&token=" + token, pathname: "/account-setup.html"},
      history: {replaceState: (...args) => {replaced.push(args);}}},
    document: {getElementById: id => nodes[id]},
    URLSearchParams, TextEncoder,
    fetch: async (url, options) => {calls.push({url, options}); return {ok: true};}
  };
  runInNewContext(script, context, {timeout: 1000});
  assert.equal(replaced.length, 1, "fragment must be removed immediately");
  assert.equal(replaced[0][2], "/account-setup.html");
  return {nodes, events, calls};
}

for (const [purpose, endpoint, eventName] of [
  ["INITIAL_PASSWORD", "/api/auth/recovery/initial-password", "password"],
  ["RESET_PASSWORD", "/api/auth/recovery/reset", "password"],
  ["VERIFY_EMAIL", "/api/auth/recovery/email/confirm", "confirm"]
]) {
  const {nodes, events, calls} = simulate(purpose);
  assert.equal(typeof events[eventName], "function");
  assert.equal(nodes[eventName === "confirm" ? "confirm-form" : "password-form"].hidden, false);
  await events[eventName]({preventDefault() {}});
  assert.equal(calls.length, 1);
  assert.equal(calls[0].url, endpoint);
  assert.equal(calls[0].options.credentials, "omit");
  assert.equal(calls[0].options.referrerPolicy, "no-referrer");
  assert.equal(calls[0].options.cache, "no-store");
  const body = JSON.parse(calls[0].options.body);
  assert.equal(body.token.length, 43);
  assert.equal(body.password !== undefined, eventName === "password");
  assert.equal(nodes["new-password"].value, "");
  assert.equal(nodes["confirm-password"].value, "");
  assert.equal(nodes[eventName === "confirm" ? "confirm-form" : "password-form"].hidden, true);
  assert.equal(nodes.status.textContent.includes("A".repeat(43)), false);
}

for (const [purpose, token, secure] of [
  ["INITIAL_PASSWORD", "bad", true],
  ["UNSUPPORTED", "A".repeat(43), true],
  ["INITIAL_PASSWORD", "A".repeat(43), false]
]) {
  const {nodes, events, calls} = simulate(purpose, token, secure);
  assert.equal(nodes["password-form"].hidden, true);
  assert.equal(nodes["confirm-form"].hidden, true);
  assert.equal(Object.keys(events).length, 0);
  assert.equal(calls.length, 0);
}

const weak = simulate("INITIAL_PASSWORD");
weak.nodes["new-password"].value = "short";
weak.nodes["confirm-password"].value = "short";
await weak.events.password({preventDefault() {}});
assert.equal(weak.calls.length, 0, "weak secrets must not be sent");

const tooManyBytes = simulate("RESET_PASSWORD");
tooManyBytes.nodes["new-password"].value = "é".repeat(40);
tooManyBytes.nodes["confirm-password"].value = "é".repeat(40);
await tooManyBytes.events.password({preventDefault() {}});
assert.equal(tooManyBytes.calls.length, 0, "BCrypt byte limit must be enforced");

assert.match(html, /Content-Security-Policy/);
assert.match(html, /referrer.*no-referrer/);
assert.doesNotMatch(html, /https?:\/\//, "no remote scripts/styles/tracking");
console.log("PASS: three supported recovery flows, token stripping and secure client-side rejection");
