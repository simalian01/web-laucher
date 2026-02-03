# Web Laucher

一个基于 Android WebView 的自定义 Launcher（默认桌面），主屏幕即多窗口浏览器，左右翻页切换固定网页窗口，上滑打开应用抽屉，并支持保活与崩溃恢复。

## 功能概览
- Launcher 桌面（HOME）模式，支持“设置为默认桌面”引导。
- 主屏幕 ViewPager2 多窗口 WebView，左右滑动切换。
- WebView 不随切页销毁，支持文件上传、输入交互、Cookie 共享。
- 上滑打开应用抽屉：搜索框 + 已安装应用列表。
- 前台保活服务 + 网络变化提示与自动刷新策略。
- WebView 崩溃自动重建，尽量恢复上次 URL 与滚动位置。
- 设置页：窗口管理（添加/编辑/删除/拖拽排序）、在线策略、WebView 选项、返回键策略与调试开关。

## 设置为默认桌面
1. 打开应用，进入设置页。
2. 点击“设置为默认桌面”按钮。
3. 在系统默认应用设置中选择本应用作为默认桌面。

## 在 App 内添加/编辑 URL
1. 进入设置页。
2. 在“窗口管理”列表中点击“添加窗口”，输入名称与 URL。
3. 点击编辑按钮可修改窗口信息；拖拽列表可排序。

## 关闭电池优化
1. 进入设置页。
2. 点击“忽略电池优化”按钮，按系统提示允许。
3. 某些厂商（如 MIUI/EMUI）可能需要额外在系统后台管理中手动授权。

## GitHub Actions 手动构建 APK
该仓库提供手动触发工作流：`.github/workflows/manual-build-apk.yml`。

1. 进入 GitHub 仓库的 **Actions**。
2. 选择 **Manual Build APK**。
3. 点击 **Run workflow**，默认构建 debug APK。
4. 运行完成后在 **Artifacts** 下载 `apk`。

## 常见问题
- **WebView 被杀/白屏**：应用已实现 `onRenderProcessGone` 自动重建，可尝试切换页面或手动刷新。
- **厂商限制后台**：请在系统后台管理中手动允许自启动/后台运行，并关闭电池优化。
- **网络恢复后未刷新**：在设置页“网络恢复自动刷新”中选择“刷新当前/全部”。

## PR 分支脚本
如果当前环境无法直接创建 PR，可使用脚本：

```bash
bash scripts/create_pr_branch.sh
```

该脚本会自动：
- 创建符合 `YYYYMMDD-HHMMSS` 的新分支
- git add/commit
- push origin 新分支
- 若 `gh` 可用则创建 PR
