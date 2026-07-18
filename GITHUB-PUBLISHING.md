# GitHub 发布步骤

## GitHub 能做什么

GitHub 仓库用于保存 Android 客户端源码、自动构建 APK、提供 Release 下载和版本记录。GitHub 不能代替实时远程控制所需的云端中继。

## 首次创建仓库

1. 登录 GitHub，创建一个新仓库，例如 `video-flow-android`。
2. 初期建议选择 **Private**，确认没有敏感信息后再改为 Public。
3. 不要让 GitHub 自动生成 README、`.gitignore` 或 License，因为本目录已经包含这些文件。
4. 在本目录初始化并推送：

```powershell
git init
git add .
git commit -m "Android 2.0 native client"
git branch -M main
git remote add origin https://github.com/你的用户名/video-flow-android.git
git push -u origin main
```

## 生成下载版本

推送版本标签后，GitHub Actions 会自动构建测试 APK并放入 Releases：

```powershell
git tag v2.0.0
git push origin v2.0.0
```

用户随后可以进入仓库右侧的 **Releases** 下载 APK。

## 正式公开前必须完成

- 生成并离线备份正式 Android 签名密钥。
- 把签名密码配置为 GitHub Actions Secrets，禁止写进仓库。
- 使用 Release 构建替代 Debug 构建，确保后续版本可以覆盖安装。
- 确定开源许可证；未明确许可证前，默认不授权他人复制或再发布源码。
- 将临时 `trycloudflare.com` 地址替换为固定云端域名。
