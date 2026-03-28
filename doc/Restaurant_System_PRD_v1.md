# 🍜 Restaurant Management System PRD v1.0

---

# 1. Product Overview

## 1.1 Objective
Build an end-to-end restaurant management system for small to mid-sized restaurant chains, covering:

- Front POS ordering
- Kitchen order processing (KDS)
- Order fulfillment
- Inventory management (BOM-based)
- Operational analytics

### Core Goals
- Improve order accuracy
- Increase kitchen efficiency
- Reduce inventory waste
- Enable data-driven decisions

---

## 1.2 Target Users

| Role | Needs |
|------|------|
| Owner | Revenue, profit, multi-store insights |
| Store Manager | Daily operations, staff, inventory |
| Front Staff | Fast and accurate ordering |
| Kitchen Staff | Clear task workflow |
| Procurement | Inventory tracking and restocking |

---

# 2. MVP Scope

## 2.1 In Scope (P0)

### Core Modules
- Menu Management
- POS Ordering System
- Order Management
- Kitchen Display System (KDS)
- Order Fulfillment (Pickup / Dine-in)
- Inventory (BOM deduction)
- Basic Reports

---

## 2.2 Out of Scope (Later Phases)

- AI analytics & recommendations
- Marketing automation
- Ads integration
- Advanced payroll system
- Central kitchen / multi-warehouse

---

# 3. System Architecture Overview

## 3.1 Core Domains

1. Master Data
2. Order System
3. Kitchen Execution
4. Inventory
5. Reporting
6. Configuration
7. User & Permission

---

# 4. Core Business Flow

## 4.1 Order Lifecycle
Create Order → Submit → In Preparation → Ready → Completed

↓

Cancel / Refund

---

## 4.2 Kitchen Workflow
Order → Split into Tasks → Assigned to Stations → Cooking → Assembly → Ready


---

## 4.3 Inventory Flow
Order Completed → Deduct Inventory (via BOM) → Check Threshold → Alert


---

# 5. Core Modules Design

---

# 5.1 Menu Management

## Features
- Category management
- Item management
- Item status (active / inactive / sold out)
- Variants (size, portion)
- Add-ons / toppings
- Dietary tags
- Store-specific menu

---

# 5.2 POS Ordering System

## Features
- Dine-in / Pickup orders
- Table number / Pickup ID
- Item selection with modifiers
- Notes & special requests
- Discount / coupon
- Order editing / cancellation

---

# 5.3 Order Management

## Order Entity

### Order
- order_id
- store_id
- order_type
- table_number / pickup_number
- status
- total_price
- discount
- created_time

### Order Item
- item_id
- name
- quantity
- price
- station
- notes

### Order Item Option
- option_name
- price_delta

---

## Order Status

### Main Status
- Draft
- Submitted
- In Preparation
- Ready
- Completed
- Cancelled
- Refunded

---

# 5.4 Kitchen Display System (KDS)

## Key Concept: Configurable Stations

### Example Stations
- Noodle
- Grill
- Fry
- Beverage
- Assembly

---

## Workflow

- Orders auto-split into station tasks
- Each station sees only relevant items
- Tasks move through:
  - Pending
  - In Progress
  - Done

---

## Special Logic
- Fallback station if not configured
- Final "Assembly" station confirms completion

---

# 5.5 Order Fulfillment

## Features
- Pickup screen
- Order ready notification
- Completed tracking
- Delay monitoring

---

# 5.6 Inventory Management

## Concepts

### Theoretical Inventory
System-calculated based on:
- Purchases
- Orders (BOM deduction)

### Actual Inventory
Manual count by staff

---

## Features
- Ingredient tracking
- BOM mapping
- Low stock alert
- Inventory adjustment
- Stock discrepancy tracking

---

# 5.7 Reporting & Analytics

## Owner Dashboard
- Revenue (daily / weekly / monthly)
- Top selling items
- Store comparison
- Profit estimation
- Inventory loss

---

## Store Manager Dashboard
- Daily sales
- Order volume
- Peak hours
- Low stock alerts
- Staff performance (future)

---

## Kitchen Metrics
- Order prep time
- Delayed orders
- Station workload

---

# 5.8 Configuration Module

## Features
- Station setup
- Menu-to-station mapping
- Store configuration
- Business rules
- Feature toggles

---

# 5.9 User & Permission System

## Roles
- Owner
- Manager
- Front Staff
- Kitchen Staff

---

## Permission Examples
- Create order
- Edit order
- Refund
- Manage menu
- View reports
- Manage inventory

---

# 6. Data Model (Simplified)

## Core Entities

- Store
- User
- MenuItem
- Order
- OrderItem
- KitchenTask
- InventoryItem
- InventoryTransaction
- BOM
- Station

---

# 7. MVP Roadmap

## Phase 1 (Core System)
- POS
- Orders
- KDS
- Basic inventory
- Basic reports

---

## Phase 2 (Operations)
- Refund workflow
- Inventory alerts
- Staff scheduling
- Multi-store support

---

## Phase 3 (Advanced)
- AI analytics
- Marketing tools
- Ads integration
- Central kitchen

---

# 8. Key Design Principles

- Configuration-driven (not hardcoded)
- Modular architecture
- Real-time updates
- Scalable for multi-store
- Simple UX for frontline staff

---

# 9. Success Metrics

- Order processing time ↓
- Order error rate ↓
- Kitchen efficiency ↑
- Inventory waste ↓
- Revenue visibility ↑

---

# 10. Future Expansion

- AI recommendation engine
- Dynamic pricing
- Supply chain optimization
- Customer app integration

---