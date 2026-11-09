# NekoBox for Android

[![API](https://img.shields.io/badge/API-21%2B-brightgreen.svg?style=flat)](https://android-arsenal.com/api?level=21)
[![Releases](https://img.shields.io/github/v/release/MatsuriDayo/NekoBoxForAndroid)](https://github.com/MatsuriDayo/NekoBoxForAndroid/releases)
[![License: GPL-3.0](https://img.shields.io/badge/license-GPL--3.0-orange.svg)](https://www.gnu.org/licenses/gpl-3.0)

A sing-box-based universal proxy client for Android, forked and extended for
personal Android pentesting work.

## 📌 About This Fork

This is a personal fork of [MatsuriDayo/NekoBoxForAndroid](https://github.com/MatsuriDayo/NekoBoxForAndroid).
Changes from upstream:

* Removed the "Ads" nav-drawer item that linked out to an external promo page
* **Kill Switch** shortcut in Settings that deep-links to Android's native VPN settings,
  so you can enable "Always-on VPN" + "Block connections without VPN" for OS-level
  fail-closed behavior if the tunnel ever dies or crashes
* Quick-add profile presets for **Burp Suite**, **mitmproxy**, and **OWASP ZAP**
  (HTTP outbound to `127.0.0.1:8080`); each one probes the port first and warns if
  nothing is listening there yet
* **Install CA Certificate** shortcut in Settings, using Android's `KeyChain` installer,
  for trusting a Burp/ZAP/mitmproxy CA to intercept HTTPS
* Native **Traffic Log** screen (nav drawer) — a live, searchable view of sing-box's
  Clash API connections (host, rule, chain, upload/download). Tap a connection for full
  detail (source/destination IP+port, rule payload, process), then **Copy** it, **Block
  domain** (adds a Route rule), or **Add to proxy scope** (adds that app to per-app
  routing) straight from there
  * Export the current list as JSON or CSV
  * Optional **auto-export on VPN disconnect** (Settings), saved to the app's own
    storage before the Clash API port goes down
* **Secure Browsing**: one-tap Settings action that downloads the
  [HaGeZi Threat Intelligence Feeds](https://github.com/hagezi/dns-blocklists) list
  (up to 5000 malware/phishing domains, no account needed) into a single Route rule;
  toggle or delete it like any other rule afterward
* Upgraded the UI to **Material 3** (including fixing the crash and flat-card look that
  came with that change) and set the default theme color to blue instead of pink
* Icons across Settings and toolbar/popup menus now tint to the selected accent color
  instead of a fixed gray
* Fixed native build scripts for macOS (NDK host path) and for 16 KB page-size devices

The built-in Clash API dashboard (`Settings > Enable Clash API`, then `Dashboard` in the
nav drawer) still works as before, as does the Quick Settings Tile and the notification's
Stop action — both already existed upstream and are useful as quick one-tap VPN toggles.

Still licensed under GPLv3, same as upstream — see [LICENSE](LICENSE).

---

## Downloads

[![GitHub All Releases](https://img.shields.io/github/downloads/Matsuridayo/NekoBoxForAndroid/total?label=downloads-total&logo=github&style=flat-square)](https://github.com/Matsuridayo/NekoBoxForAndroid/releases)

This fork has no releases of its own yet — build it from source (see
[buildScript/](buildScript) and [libcore/build.sh](libcore/build.sh)), or grab the
upstream releases: https://github.com/Matsuridayo/NekoBoxForAndroid/releases

**The Google Play version of the upstream app has been controlled by a third party since
May 2024 and is a non-open-source version. Please do not download it.**

## Changelog & Telegram Channel (upstream)

https://t.me/Matsuridayo

## Homepage & Documents (upstream)

https://matsuridayo.github.io

## Supported Proxy Protocols

* SOCKS (4/4a/5)
* HTTP(S)
* SSH
* Shadowsocks
* VMess
* Trojan
* VLESS
* AnyTLS
* ShadowTLS
* TUIC
* Hysteria 1/2
* WireGuard
* Trojan-Go (trojan-go-plugin)
* NaïveProxy (naive-plugin)
* Mieru (mieru-plugin)

Please visit [here](https://matsuridayo.github.io/nb4a-plugin/) to download plugins for
full proxy support.

## Supported Subscription Formats

* Some widely used formats (like Shadowsocks, ClashMeta and v2rayN)
* sing-box outbound

Only resolving outbound, i.e. nodes, is supported. Information such as diversion rules
are ignored.

## Credits

Core:

- [SagerNet/sing-box](https://github.com/SagerNet/sing-box)

Android GUI:

- [shadowsocks/shadowsocks-android](https://github.com/shadowsocks/shadowsocks-android)
- [SagerNet/SagerNet](https://github.com/SagerNet/SagerNet)

Web Dashboard:

- [Yacd-meta](https://github.com/MetaCubeX/Yacd-meta)
