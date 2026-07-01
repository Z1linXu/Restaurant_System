# Pad App Local Development

Current status: Pad App POC workspace with Android WebView shell files and native TCP printer test bridge. Android build still requires a local Android SDK / Android Studio environment.

## Architecture Reminder

There is only one backend in the current system:

```text
Restaurant_System/backend
```

`restaurant-pad-app` is not a backend. It is an Android client shell that loads the Restaurant_System frontend and, in later work, will execute local printer operations from the Android device.

During local development:

```text
Android Pad App
  -> runtime API base: http://{developer-lan-ip}:8080
  -> bundled frontend artifact: android/app/src/main/assets/web
  -> local printer example: 192.168.2.200:9100
```

The development computer, Android Pad, and printers must be on the same LAN when testing native printer behavior.

## Current Restaurant_System Run Commands

Backend:

```bash
cd backend
mvn spring-boot:run
```

Frontend dev server:

```bash
cd frontend
npm run dev
```

Frontend LAN production preview:

```bash
cd frontend
npm run build
npm run preview:lan
```

Build the frontend and copy it into Android assets:

```bash
cd frontend
npm run build
cd ..
bash restaurant-pad-app/scripts/copy-frontend-dist.sh
```

## API Base Configuration

Configure the Android Pad App runtime API base to the development computer LAN address:

```text
http://{developer-lan-ip}:8080
```

Examples:

```text
http://192.168.2.140:8080
http://192.168.2.33:8080
```

Do not use Android's own `localhost` or `127.0.0.1` to mean the development computer. Inside Android, `localhost` points back to the Android device itself.

Do not hardcode the development computer IP in source code. It should be entered through runtime settings/native preferences during local testing.

## Native Printer Bridge Local Test

The PR4 native printer test page is a hardware connectivity POC only.

It does not:

- print real restaurant orders
- call backend `print_jobs`
- claim print jobs
- call complete/fail/release
- use backend receipt business logic

Use the local test printer example:

```text
Printer IP: 192.168.2.200
Port: 9100
Timeout: 3000 or 5000 ms
```

Recommended test sequence:

1. Put the Android Pad on the same Wi-Fi/LAN as the printer.
2. Confirm the backend computer is also reachable on the same LAN if the WebView needs API access.
3. Open the Pad printer test page: `https://restaurant-pad.local/printer-test.html`.
4. Enter `192.168.2.200`, port `9100`, and timeout `3000` or `5000`.
5. Tap `Test Connection`.
6. If the connection succeeds, tap `Print Test Receipt`.

`192.168.2.200` is only an example test printer address. Real deployments must read the printer IP from a settings screen, runtime config, backend printer config, or operator input.

## LAN / Subnet Checklist

For first-pass local testing, keep all three devices in the same subnet:

```text
Android Pad: 192.168.2.x
Backend computer: 192.168.2.x
Printer: 192.168.2.200
```

`192.168.1.x` and `192.168.2.x` are usually different `/24` networks. They normally cannot directly reach each other unless the router is configured for cross-subnet routing or the subnet mask allows it.

If connection fails, check:

- Android Pad IP address
- backend computer IP address
- printer IP address
- subnet mask
- gateway
- router AP isolation / client isolation
- printer port `9100`
- Android local-network socket permission / cleartext debug config
- whether the printer has a fixed IP or DHCP reservation

## Configuration Rules

- Do not hardcode developer IP addresses in source code.
- Do not hardcode printer IP addresses in source code.
- Do not rely on Vite proxy inside Android WebView.
- Runtime API base must be configurable in Pad native preferences or a setup screen.
- Printer IP must come from user input, runtime config, or backend printer configuration.
- Android debug builds may allow cleartext HTTP only for local development.
- Production should use HTTPS for backend API traffic.

## Not Yet Available

- production background print worker inside Android
- production device pairing / cloud config
- real order print job polling from Android
- automated physical printer QA
