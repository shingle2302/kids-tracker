# 儿童安全守护 - 商业级 UI 重构与矢量图标设计方案

## 1. 概述
为提升“儿童安全守护”应用的商业化品质，对 **家长端 (Parent App)** 和 **孩子端 (Child App)** 的 UI 界面、视觉设计语言 (Design System) 以及应用图标 (App Launcher Icons) 进行全面升级重构。

---

## 2. 设计系统与色彩定义 (Design System)

### 2.1 家长端 (Guardian Theme)
- **主色 (Primary)**: `#2563EB` (Royal Blue)
- **容器主色 (Primary Container)**: `#DBEAFE`
- **辅色 (Secondary)**: `#0F172A` (Slate Dark)
- **状态指示色**:
  - 在线/正常: `#10B981` (Emerald Green)
  - 警告/低电量: `#F59E0B` (Amber)
  - 离线/异常: `#EF4444` (Rose Red)

### 2.2 孩子端 (Child Safety Theme)
- **主色 (Primary)**: `#0D9488` (Teal Dark)
- **容器主色 (Primary Container)**: `#CCFBF1`
- **辅色 (Secondary)**: `#0284C7` (Sky Blue)
- **状态指示色**:
  - 守护激活: `#10B981` (Pulse Emerald)
  - 守护未开启: `#6B7280` (Cool Gray)

---

## 3. 矢量应用图标 (Adaptive Launcher Icons)

### 3.1 家长端图标 (`parent`)
- **背景 (`ic_launcher_background.xml`)**: 深蓝色矢量图案。
- **前景 (`ic_launcher_foreground.xml`)**: 精美双手守护心形与雷达波纹矢量图案。

### 3.2 孩子端图标 (`child`)
- **背景 (`ic_launcher_background.xml`)**: 青绿色矢量图案。
- **前景 (`ic_launcher_foreground.xml`)**: 矢量安全徽章与守护之星图案。

---

## 4. UI 界面重构细节

### 4.1 家长端界面 (`parent/MainActivity.kt`)
- **沉浸式地图布局**: 高德地图全屏渲染，地图右侧带控制卡片。
- **可滑动底部抽屉 (Modal Bottom Sheet)**:
  - **头部状态栏**: 设备在线状态、实时电池电量条、当前详细地址与更新时间。
  - **后端/配对控制区**: 优雅的输入框与圆角绑定按钮、快速模拟发包/定位刷新按钮。

### 4.2 孩子端界面 (`child/MainActivity.kt`)
- **守护看板 Hero Card**:
  - “守护服务激活中”呼吸灯与提示。
  - 6 位配对码大字号高亮显示，带有“重置”按钮。
  - 后端 URL 配置卡片。
- **定位上报核心卡片**: 一键触发高德 GPS 真实定位并同步云端。
- **步骤式防卸载与权限引导卡片**:
  - Step 1: 常规定位/通知权限
  - Step 2: 后台定位 (始终允许)
  - Step 3: 设备管理器防卸载
  - Step 4: 辅助功能防强关
  - Step 5: 后台守护服务开关
