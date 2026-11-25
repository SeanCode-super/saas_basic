"use strict";
const $ = (id) => document.getElementById(id);
let session, current, viewVersion = 0, editing, requestId;
function element(tag, text) {
  const node = document.createElement(tag);
  if (text != null) node.textContent = text;
  return node;
}
function brand(name) {
  document.title = name + " · 管理后台";
  for (const node of document.querySelectorAll(".brand-name")) node.textContent = name;
}
async function api(path, body) {
  const options = body === undefined ? {} : { method: "POST", headers: {"Content-Type":"application/json"}, body: JSON.stringify(body) };
  const reply = await fetch(path, { ...options, credentials: "same-origin", cache: "no-store" });
  const data = await reply.json();
  if (!reply.ok) {
    if (reply.status === 401 && session) showLogin();
    throw Object.assign(new Error(data.message || "请求失败，请重试"), {status:reply.status});
  }
  return data;
}
function showLogin() {
  session = null;
  current = null;
  viewVersion++;
  if ($("dialog").open) $("dialog").close();
  $("workspace").hidden = true;
  $("login").hidden = false;
  $("login-form").reset();
  $("login-form").elements.username.focus();
}
async function loadSession() {
  session = await api("/console/session");
  brand(session.name);
  $("tenant").textContent = session.tenant;
  $("username").textContent = session.username;
  $("nav").replaceChildren();
  for (const page of session.pages) {
    const button = element("button", page.label);
    button.dataset.key = page.key;
    button.onclick = () => openPage(page);
    $("nav").append(button);
  }
  $("login").hidden = true;
  $("workspace").hidden = false;
  if (session.pages.length) await openPage(session.pages[0]);
  else $("status").textContent = "当前账号尚未分配业务页面";
}
function command(name, onClick, primary = false) {
  const button = element("button",name);
  if (primary) button.className = "primary";
  button.onclick = onClick;
  return button;
}
function renderTable(page, rows) {
  const table = element("table");
  const headings = table.createTHead().insertRow();
  for (const col of page.columns) {
    const th = element("th",col.label);
    th.scope = "col";
    headings.append(th);
  }
  const body = table.createTBody();
  for (const record of rows) {
    const tr = body.insertRow();
    for (const col of page.columns) {
      const raw = record[col.key];
      const cell = tr.insertCell();
      cell.dataset.label = col.label;
      cell.textContent = page.values[col.key]?.[raw] ?? raw ?? "—";
    }
  }
  if (!rows.length) {
    const cell = body.insertRow().insertCell();
    cell.colSpan = page.columns.length;
    cell.className = "empty";
    cell.textContent = "暂无" + page.label;
  }
  $("table-area").replaceChildren(table);
}
async function openPage(page) {
  const version = ++viewVersion;
  current = page;
  for (const b of $("nav").children) {
    if (b.dataset.key === page.key) b.setAttribute("aria-current", "page");
    else b.removeAttribute("aria-current");
  }
  $("page-title").textContent = page.label;
  $("actions").replaceChildren(command("刷新", () => openPage(page)));
  for (const action of page.actions) $("actions").append(command(action.label, () => openAction(action), true));
  $("status").textContent = "";
  $("table-area").replaceChildren(element("p","加载中…"));
  try {
    const rows = await api("/console/actions/"+page.key+"_list",{input:{},requestId:crypto.randomUUID()});
    if (version === viewVersion) renderTable(page,rows);
  } catch (error) {
    if (version === viewVersion) {
      $("table-area").replaceChildren();
      $("status").textContent = error.message;
    }
  }
}
async function openAction(action) {
  const page = current;
  $("status").textContent = "";
  try {
    const options = await api("/console/options/"+action.key);
    if (current !== page || $("dialog").open) return;
    editing = action;
    requestId = crypto.randomUUID();
    $("dialog-title").textContent = action.label;
    $("fields").replaceChildren();
    $("form-error").textContent = "";
    for (const field of action.fields) {
      const label = element("label",field.label);
      const choices = options[field.key];
      const control = element(choices ? "select" : "input");
      if (choices) {
        control.multiple = field.type === "number_list";
        for (const {value,label} of choices) control.add(new Option(label,value));
        if (!choices.length) label.append(element("small","当前账号没有可选项"));
      } else {
        control.type = field.type === "number" ? "number" : "text";
        if (control.type === "number") control.step = "any";
        if (field.type === "number_list") control.placeholder = "多个编号用逗号分隔";
      }
      control.required = field.required;
      control.name = field.key;
      label.append(control);
      $("fields").append(label);
    }
    $("dialog").showModal();
  } catch (error) { $("status").textContent = error.message; }
}
$("login-form").onsubmit = async (event) => {
  event.preventDefault();
  const form = event.currentTarget, submit = form.querySelector("button");
  submit.disabled = true;
  $("login-error").textContent = "";
  try {
    await api("/console/login", Object.fromEntries(new FormData(form)));
    form.reset();
    await loadSession();
  } catch (error) { $("login-error").textContent = error.status === 401 ? "账号或密码有误，或账号无权访问" : error.message; }
  finally { submit.disabled = false; }
};
$("logout").onclick = async () => {
  try { await api("/console/logout",{}); showLogin(); }
  catch (error) { $("status").textContent = error.message; }
};
$("cancel").onclick = () => $("dialog").close();
$("action-form").oninput = () => { requestId = crypto.randomUUID(); };
$("action-form").onsubmit = async (event) => {
  event.preventDefault();
  const submit = event.currentTarget.querySelector("[type=submit]");
  const input = {};
  for (const field of $("fields").querySelectorAll("input,select")) {
    input[field.name] = field.multiple ? Array.from(field.selectedOptions,o => o.value).join(",") : field.value;
  }
  submit.disabled = true;
  try {
    await api("/console/actions/"+editing.key,{requestId,input});
    $("dialog").close();
    await openPage(current);
  } catch (error) { $("form-error").textContent = error.message; }
  finally { submit.disabled = false; }
};
(async () => {
  try {
    const about = await api("/console/about");
    brand(about.name);
    if (about.signedIn) await loadSession();
    else showLogin();
  }
  catch (error) {
    showLogin();
    if (error.status !== 401) $("login-error").textContent = error.message;
  }
})();
