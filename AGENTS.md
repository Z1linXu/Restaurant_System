# AGENTS.md
Rules for Codex:
- Always update SYSTEM_DOCUMENTATION.md
- Never assume missing API
- Follow existing naming
- Keep UI minimal (iPad friendly)

## 📌 Project Overview

Restaurant Management System (MVP)

This system includes:

* POS ordering system (front desk)
* Kitchen Display System (KDS)
* Ready / pickup screen
* Inventory management (basic)
* Admin dashboard (later phase)

---

## 🧱 Tech Stack

### Backend

* Spring Boot 3
* PostgreSQL
* MyBatis-Plus
* WebSocket (for realtime updates)

### Frontend

* React (Web)
* Ant Design (Admin pages)
* Touch-friendly UI (POS / KDS / Ready screen)

### Architecture

* Web-first approach (single frontend for all devices)
* Mobile/tablet accessed via browser (iPad / Android)

---

## 📁 Project Structure

restaurant-system/

* docs/
* backend/
* frontend/
* AGENTS.md

---

## 📚 Documentation Rules

Before coding, ALWAYS read:

* docs/DatabaseDesign.md
* docs/API_bilingual.md
* docs/MVP_Scope.md (if exists)

Rules:

* Do NOT rename tables or fields without confirmation
* Follow database schema strictly
* Follow API contract strictly

---

## 🧩 Backend Module Design

Modules must be separated clearly:

* order
* kitchen
* inventory
* menu
* user
* station

Each module should include:

* controller
* service
* mapper
* entity

---

## ⚙️ Backend Coding Rules

### General

* Use MyBatis-Plus BaseMapper
* Use consistent naming with database
* Use DTO for API requests/responses when needed

### Transactions (IMPORTANT)

Must use transaction management for:

* submitOrder
* completeKitchenTask
* executePrep
* restockInventory

---

## 🔄 Core Business Flow (MVP)

### Order Flow

draft → submitted → preparing → ready → picked_up → completed

### When submitting an order:

* Validate order
* Create order_items
* Generate kitchen_tasks
* Deduct inventory (via BOM)
* Create inventory_transactions

### When completing kitchen task:

* Update task status
* If all tasks done → mark order ready

---

## 📡 Realtime Rules (WebSocket)

* POS submits order → KDS updates immediately
* Kitchen completes task → Ready screen updates
* Use event-based push model

---

## 🎨 Frontend Rules

### POS / KDS / Ready Screen

* Must be touch-friendly
* Large buttons
* Card-based layout
* Minimal Ant Design usage

### Admin Dashboard

* Can use Ant Design heavily
* Table-based + filter + analytics

---

## 🚫 Scope Control (VERY IMPORTANT)

Do NOT implement these in MVP unless explicitly requested:

* Authentication / authorization
* Payment integration
* Advanced reporting
* Multi-store support
* Complex role permissions

---

## 🧠 Development Workflow (MANDATORY)

Before coding:

1. Summarize the task
2. List files to modify
3. Highlight ambiguity or missing info

During coding:

* Keep changes minimal and focused
* Follow module boundaries

After coding:

1. Summarize what was implemented
2. Explain how to run/test
3. Mention any assumptions made

---

## 🧪 Code Quality

* Keep code simple and readable
* Avoid over-engineering
* Prefer explicit logic over magic
* Write clear method names

---

## ⚠️ Important Constraints

* This is a solo-developer MVP
* Prioritize speed + clarity over perfection
* Business logic correctness > architecture perfection

---

## 🎯 Priority Order

Always prioritize in this order:

1. Order submission flow
2. Kitchen task generation
3. Inventory deduction
4. Realtime updates
5. UI polish
6. Admin features

---

## 🚀 Goal

The goal is to deliver a working system where:

POS → Submit Order → Kitchen → Ready → Complete
AND
Inventory is correctly deducted and tracked.

---
