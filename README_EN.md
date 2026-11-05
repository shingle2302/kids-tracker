# Kids Tracker - Smart Child Safety & Anti-Lost System

> High-precision AMap GPS tracking, real-time safety distance alert, and 3-layer security authentication.
> Version: v3.0.0 (Commercial & Security Plus)

---

## Architecture

- **:child (Child App)**: Runs on the child's device, provides foreground/background continuous high-precision GPS positioning, 10-minute dynamic pairing code (OTP), and anti-uninstall protection.
- **:parent (Parent App)**: Runs on the parent's device, featuring an immersive full-screen AMap tracking interface, 5-second auto-refresh polling, and safe distance fence alerts (vibration + ringtone + manual silence).
- **:backend (Spring Boot Backend)**: Spring Boot 3.3 + H2 JPA RESTful server handling device pairing, location relay, command dispatch, and security auth.
- **:core (Core Library)**: Shared API service definitions and Retrofit data models.

---

## 3-Layer Security & Privacy Architecture

1. **Dynamic 10-Min OTP**: Pairing codes expire after 10 minutes and automatically rotate, preventing static code leakage and brute-force tracking.
2. **Parent Account & JWT Auth**: Parents register/login with secure tokens (`Authorization: Bearer <Token>`).
3. **Authorization Isolation**: The backend verifies Master Guardian bindings so unauthorized users cannot access child location data.

---

## Core Features

### 1. Material 3 Commercial UI
- **Parent App**: Full-screen AMap, top status bar with 5-second live polling, bottom control card showing battery level, last reported time, and exact address.
- **Child App**: Guardian-teal theme, large dynamic pairing code card, countdown timer, and step-by-step security permission guide.

### 2. Safe Distance Fence & Alarm
- **Customizable Radius**: 50m, 100m, 200m, 500m, 1000m.
- **Vibration & Ringtone Linkage**: Triggers strong vibration and alarm sound when the child exceeds the safe distance.
- **Manual Silence**: Parents can tap the "Stop Ringing/Vibration" button at any time.

---

## Quick Start

1. **Start Backend**:
   ```bash
   ./gradlew :backend:bootRun
   ```
   Server runs at `http://localhost:8080/` (Emulator: `http://10.0.2.2:8080/`).
2. **Build & Deploy Android Apps**:
   - Install `:child` on child device and grant location permissions.
   - Install `:parent` on parent device, open Settings to enter the 6-digit dynamic pairing code.
