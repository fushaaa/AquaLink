let authorization = "";
let currentOrders = [];
let pendingIdempotencyKey = null;
let pendingRequestBody = null;

const signInView = document.querySelector("#sign-in-view");
const residentView = document.querySelector("#resident-view");
const signInForm = document.querySelector("#sign-in-form");
const signInError = document.querySelector("#sign-in-error");
const orderForm = document.querySelector("#order-form");
const orderError = document.querySelector("#order-error");
const householdSelect = document.querySelector("#household");
const orderList = document.querySelector("#order-list");
const appMessage = document.querySelector("#app-message");

function api(path, options = {}) {
    const headers = new Headers(options.headers || {});
    if (authorization) headers.set("Authorization", authorization);
    if (options.body && !headers.has("Content-Type")) {
        headers.set("Content-Type", "application/json");
    }
    return fetch(path, { ...options, headers }).then(async response => {
        if (!response.ok) {
            const body = await response.json().catch(() => ({}));
            throw new Error(body.message || (response.status === 401
                ? "Phone number or password was not accepted."
                : "The request could not be completed."));
        }
        if (response.status === 204) return null;
        return response.json();
    });
}

function setMessage(message, isError = false) {
    appMessage.textContent = message;
    appMessage.classList.toggle("error", isError);
}

function formatDate(value, options = {}) {
    return new Intl.DateTimeFormat(undefined, {
        month: "short", day: "numeric", hour: "numeric", minute: "2-digit", ...options
    }).format(new Date(value));
}

function localDateTimeValue(date) {
    const offset = date.getTimezoneOffset() * 60000;
    return new Date(date.getTime() - offset).toISOString().slice(0, 16);
}

function setDefaultWindow() {
    const start = new Date();
    start.setDate(start.getDate() + 1);
    start.setHours(9, 0, 0, 0);
    const end = new Date(start.getTime() + 2 * 60 * 60 * 1000);
    document.querySelector("#window-start").value = localDateTimeValue(start);
    document.querySelector("#window-end").value = localDateTimeValue(end);
    const min = localDateTimeValue(new Date());
    document.querySelector("#window-start").min = min;
    document.querySelector("#window-end").min = min;
}

function showStatus(order) {
    const target = document.querySelector("#latest-order");
    if (!order) {
        target.innerHTML = '<div class="empty-state"><span class="empty-symbol">—</span><p>No water orders yet.</p></div>';
        return;
    }
    target.innerHTML = `
        <p class="latest-kicker">ORDER ${escapeHtml(order.publicId.slice(0, 8).toUpperCase())}</p>
        <p class="latest-quantity">${Number(order.quantityL).toLocaleString()} <span>litres</span></p>
        <p class="latest-meta">${formatDate(order.windowStart)} to ${formatDate(order.windowEnd)}</p>
        <span class="latest-status">${escapeHtml(order.status.replaceAll("_", " "))}</span>`;
}

function renderOrders(orders) {
    currentOrders = orders;
    document.querySelector("#order-count").textContent = `${orders.length} ${orders.length === 1 ? "ORDER" : "ORDERS"}`;
    showStatus(orders[0]);
    if (!orders.length) {
        orderList.innerHTML = '<tr><td class="table-empty" colspan="4">No orders to show.</td></tr>';
        return;
    }
    orderList.innerHTML = orders.map(order => {
        const status = order.status.toLowerCase();
        return `<tr>
            <td>${escapeHtml(order.publicId.slice(0, 8).toUpperCase())}</td>
            <td>${Number(order.quantityL).toLocaleString()} L</td>
            <td>${formatDate(order.windowStart)} – ${formatDate(order.windowEnd)}</td>
            <td><span class="order-status ${escapeHtml(status)}">${escapeHtml(order.status.replaceAll("_", " "))}</span></td>
        </tr>`;
    }).join("");
}

function escapeHtml(value) {
    return String(value).replace(/[&<>"']/g, character => ({
        "&": "&amp;", "<": "&lt;", ">": "&gt;", "\"": "&quot;", "'": "&#39;"
    })[character]);
}

async function loadResident() {
    const [profile, households, orders] = await Promise.all([
        api("/api/v1/me"),
        api("/api/v1/households"),
        api("/api/v1/water-orders")
    ]);
    document.querySelector("#welcome-line").textContent = `Welcome, ${profile.fullName}.`;
    householdSelect.innerHTML = households.map(household =>
        `<option value="${escapeHtml(household.publicId)}">${escapeHtml(household.displayName)}</option>`
    ).join("");
    if (!households.length) {
        householdSelect.innerHTML = '<option value="">No linked household</option>';
        document.querySelector("#place-order").disabled = true;
    } else {
        document.querySelector("#place-order").disabled = false;
    }
    renderOrders(orders);
    signInView.hidden = true;
    residentView.hidden = false;
    document.querySelector("#sign-out").hidden = false;
}

signInForm.addEventListener("submit", async event => {
    event.preventDefault();
    signInError.hidden = true;
    const phone = document.querySelector("#phone").value.trim();
    const password = document.querySelector("#password").value;
    authorization = `Basic ${btoa(`${phone}:${password}`)}`;
    try {
        await loadResident();
        signInForm.reset();
    } catch (error) {
        authorization = "";
        signInError.textContent = error.message;
        signInError.hidden = false;
    }
});

orderForm.addEventListener("submit", async event => {
    event.preventDefault();
    orderError.hidden = true;
    setMessage("");
    const start = new Date(document.querySelector("#window-start").value);
    const end = new Date(document.querySelector("#window-end").value);
    if (!Number.isFinite(start.getTime()) || !Number.isFinite(end.getTime()) || start <= new Date() || end <= start) {
        orderError.textContent = "Choose a future delivery window with an end time after the start.";
        orderError.hidden = false;
        return;
    }
    const submit = document.querySelector("#place-order");
    submit.disabled = true;
    try {
        const requestBody = JSON.stringify({
            householdId: householdSelect.value,
            quantityL: Number(document.querySelector("#quantity").value),
            windowStart: start.toISOString(),
            windowEnd: end.toISOString(),
            notes: document.querySelector("#notes").value.trim() || null
        });
        if (pendingRequestBody !== requestBody) {
            pendingRequestBody = requestBody;
            pendingIdempotencyKey = crypto.randomUUID();
        }
        await api("/api/v1/water-orders", {
            method: "POST",
            headers: { "Idempotency-Key": pendingIdempotencyKey },
            body: requestBody
        });
        pendingIdempotencyKey = null;
        pendingRequestBody = null;
        setMessage("Your water order has been placed.");
        await refreshOrders();
    } catch (error) {
        orderError.textContent = error.message;
        orderError.hidden = false;
    } finally {
        submit.disabled = !householdSelect.value;
    }
});

async function refreshOrders() {
    try {
        renderOrders(await api("/api/v1/water-orders"));
    } catch (error) {
        setMessage(error.message, true);
    }
}

document.querySelector("#refresh-orders").addEventListener("click", refreshOrders);
document.querySelector("#sign-out").addEventListener("click", () => {
    authorization = "";
    currentOrders = [];
    pendingIdempotencyKey = null;
    pendingRequestBody = null;
    residentView.hidden = true;
    signInView.hidden = false;
    document.querySelector("#sign-out").hidden = true;
    setMessage("");
    signInForm.reset();
});

setDefaultWindow();