# NekoBox for Android (ฉบับภาษาไทย)

[![API](https://img.shields.io/badge/API-21%2B-brightgreen.svg?style=flat)](https://android-arsenal.com/api?level=21)
[![Releases](https://img.shields.io/github/v/release/MatsuriDayo/NekoBoxForAndroid)](https://github.com/MatsuriDayo/NekoBoxForAndroid/releases)
[![License: GPL-3.0](https://img.shields.io/badge/license-GPL--3.0-orange.svg)](https://www.gnu.org/licenses/gpl-3.0)

Read in English: [README.md](README.md)

ซอฟต์แวร์ proxy สารพัดประโยชน์สำหรับ Android ที่ใช้ sing-box เป็นแกนหลัก

## 📌 เกี่ยวกับ Fork นี้

Fork นี้แยกออกมาจาก [MatsuriDayo/NekoBoxForAndroid](https://github.com/MatsuriDayo/NekoBoxForAndroid)
ของต้นฉบับ ปรับแต่งเพื่อใช้งานในงาน pentest Android โดยเฉพาะ สิ่งที่เปลี่ยนจากต้นฉบับ:

* ลบเมนู "Ads" ใน nav drawer ที่ลิงก์ออกไปหน้าโปรโมทภายนอกออก
* เพิ่มทางลัด **Kill Switch** ใน Settings ที่พาไปหน้า VPN settings ของ Android โดยตรง
  เพื่อเปิด "Always-on VPN" + "Block connections without VPN" ให้ traffic ถูกบล็อกระดับ OS
  ทันทีถ้า tunnel ตายหรือแครช
* เพิ่มปุ่ม quick-add โปรไฟล์สำเร็จรูปสำหรับ **Burp Suite** และ **mitmproxy**
  (HTTP outbound ไปที่ `127.0.0.1:8080`) ให้ตั้งค่า intercept traffic ได้เร็วขึ้น
* แก้ build script ฝั่ง native ให้ใช้งานบน macOS ได้ (NDK host path) และรองรับ
  อุปกรณ์ 16 KB page size

ส่วนฟีเจอร์ดู connections log แบบ real-time (domain, rule, chain, ปริมาณ byte) มีอยู่ในตัวแอป
อยู่แล้ว ไม่ต้องแก้โค้ดเพิ่ม — แค่เปิด `Settings > Enable Clash API` แล้วเข้าเมนู `Dashboard`
ใน nav drawer

ยังอยู่ภายใต้สัญญาอนุญาต GPLv3 เหมือนต้นฉบับ — ดู [LICENSE](LICENSE)

---

## 下载 / Downloads

[![GitHub All Releases](https://img.shields.io/github/downloads/Matsuridayo/NekoBoxForAndroid/total?label=downloads-total&logo=github&style=flat-square)](https://github.com/Matsuridayo/NekoBoxForAndroid/releases)

[ดาวน์โหลดจาก GitHub Releases ของต้นฉบับ](https://github.com/Matsuridayo/NekoBoxForAndroid/releases)

**เวอร์ชัน Google Play ถูกบุคคลที่สามเข้าควบคุมตั้งแต่เดือนพฤษภาคม 2024 เป็นเวอร์ชันที่ไม่ใช่
open source แล้ว กรุณาอย่าดาวน์โหลด**

สำหรับ fork นี้ ยังไม่มีการปล่อย release อย่างเป็นทางการ — ต้อง build เองจาก source
(ดูวิธีใน [buildScript/](buildScript) และ [libcore/build.sh](libcore/build.sh))

## 更新日志 & Telegram 发布频道 / Changelog & ช่องทาง Telegram ของต้นฉบับ

https://t.me/Matsuridayo

## 项目主页 & 文档 / หน้าหลักโปรเจกต์ & เอกสาร (ของต้นฉบับ)

https://matsuridayo.github.io

## โปรโตคอล Proxy ที่รองรับ

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
* Trojan-Go (ปลั๊กอิน trojan-go-plugin)
* NaïveProxy (ปลั๊กอิน naive-plugin)
* Mieru (ปลั๊กอิน mieru-plugin)

ดาวน์โหลดปลั๊กอินเพิ่มเติมได้ที่ [ที่นี่](https://matsuridayo.github.io/nb4a-plugin/)
เพื่อให้รองรับ proxy ครบทุกแบบ

## รูปแบบ Subscription ที่รองรับ

* รูปแบบที่ใช้กันแพร่หลาย (เช่น Shadowsocks, ClashMeta และ v2rayN)
* sing-box outbound

รองรับเฉพาะการอ่านค่า outbound (โหนด) เท่านั้น ข้อมูลอื่น เช่น กฎการแบ่งเส้นทางจะถูกข้ามไป

## Credits

Core:

- [SagerNet/sing-box](https://github.com/SagerNet/sing-box)

Android GUI:

- [shadowsocks/shadowsocks-android](https://github.com/shadowsocks/shadowsocks-android)
- [SagerNet/SagerNet](https://github.com/SagerNet/SagerNet)

Web Dashboard:

- [Yacd-meta](https://github.com/MetaCubeX/Yacd-meta)
