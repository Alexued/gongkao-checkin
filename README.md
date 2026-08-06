# 考公打卡

公务员考试备考的每日打卡工具。原生 Android（Views + XML，非 Compose），离线可用，数据只存在手机本地。

## 功能

- **打卡** — 每日任务清单，环形进度，未完成自动结转为次日欠账，长按任务直接改
- **计时** — 秒表 + 打点，记录每道题用时，历史可回看逐题分段
- **背诵** — 百化分（24 组，等值分数自动判对）、资料分析公式（36 条，8 个分类）
- **统计** — 日历热力图、近两周完成率、做题用时对比、按天回看
- **电脑端同步** — 手机起局域网 web 服务，电脑浏览器或桌面客户端访问，凭访问码读写

## 构建

```bash
./gradlew assembleDebug
```

正式包需要签名密钥。本地把 `gongkao-release.jks` 放到仓库上一级目录，或用环境变量指定：

```bash
KEYSTORE_PATH=/path/to/gongkao-release.jks \
KEYSTORE_PASSWORD=... KEY_ALIAS=... KEY_PASSWORD=... \
./gradlew assembleRelease
```

找不到 keystore 时会回落到 debug 签名并给出警告 —— 这种产物**不能**用于线上更新（签名不一致装不上）。

## 版本

`versionCode` / `versionName` 可由环境变量 `VERSION_CODE` / `VERSION_NAME` 覆盖，CI 打 tag 时自动推导。

## 环境

- JDK 21
- compileSdk 36 / minSdk 26 / targetSdk 36
