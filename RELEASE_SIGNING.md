# Release 签名配置

`app/build.gradle.kts` 从仓库外读取签名材料，**密钥与密码永不进入版本控制**。
四项配置缺任意一项时，`release` 自动退回调试签名，`assembleRelease` 仍会产出
可安装的 `app-release.apk`，便于随时验证混淆后的行为。

## 本地配置

1. 生成 keystore（密码请自行设置，不要写进任何提交的文件）：

```bash
keytool -genkeypair -v -keystore melodia-release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias melodia
```

2. 把 `melodia-release.jks` 放在仓库根目录。`.gitignore` 已包含 `*.jks` 与
   `*.keystore`，不会被提交。

3. 在 `local.properties`（已被 gitignore）末尾追加四项：

```properties
RELEASE_STORE_FILE=melodia-release.jks
RELEASE_STORE_PASSWORD=你的密钥库密码
RELEASE_KEY_ALIAS=melodia
RELEASE_KEY_PASSWORD=你的密钥密码
```

4. 打包：

```bash
./gradlew assembleRelease
```

配置生效时产出正式签名包；未生效时产出调试签名包，两者文件名相同，可通过
`keytool -printcert -jarfile app/build/outputs/apk/release/app-release.apk`
确认实际签名者。

## CI 配置

`.github/workflows/release.yml` 在推送 `v*.*.*` 形式的 tag 时触发正式签名构建
并发布 GitHub Release，需要在仓库 Settings → Secrets and variables → Actions
下配置以下 4 个 Repository secrets：

| Secret 名 | 内容 |
|---|---|
| `RELEASE_KEYSTORE_BASE64` | `melodia-release.jks` 的 base64 编码全文 |
| `RELEASE_STORE_PASSWORD` | keystore 密码 |
| `RELEASE_KEY_ALIAS` | 固定值 `melodia` |
| `RELEASE_KEY_PASSWORD` | key 密码 |

`RELEASE_KEYSTORE_BASE64` 在流水线里解码落盘为 `melodia-release.jks` 后，
再以同名环境变量 `RELEASE_STORE_FILE`/`RELEASE_STORE_PASSWORD`/
`RELEASE_KEY_ALIAS`/`RELEASE_KEY_PASSWORD` 注入给 `signingValue()` 读取，
和本地 `local.properties` 走的是同一套读取逻辑。

生成 base64（Windows PowerShell，输出无换行的纯 base64，直接粘贴进 Secret 即可）：

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("melodia-release.jks")) | Set-Clipboard
```

（不要用 `certutil -encode`：它会在内容前后加 `-----BEGIN/END CERTIFICATE-----` 分隔行，
不是合法 base64 字符，CI 侧解码会报 `base64: invalid input`。）

发布时只需：

```bash
git tag v1.0.0
git push origin v1.0.0
```

## 桌面端发版

同一个 tag 会在 `windows-latest` 上并行打桌面端安装包，与 APK 一起上传到同一个 Release；
任一端失败都不会创建 Release。桌面端不需要额外配置 Secret。

| 版本 | 产物 |
|---|---|
| 正式版 `v1.2.3` | `Melodia-v1.2.3-windows-x64.msi` 与 `Melodia-v1.2.3-windows-x64.zip`（免安装版） |
| 预发布 `v1.2.3-beta.1` / `-rc.1` | 只有免安装版 zip |

- 预发布不出 MSI：MSI 判断升级只比较三段数字版本，beta 与正式版同为 `1.2.3` 时，装了 beta 的机器升不到正式版
- libmpv 按 `gradle.properties` 的 `libmpvReleaseTag` / `libmpvSha256` 下载并校验；升级 libmpv 时在
  [mpv-winbuild-cmake](https://github.com/rinchao0721/mpv-winbuild-cmake) 勾选 `publish` 运行 `libmpv audio` 工作流，
  再把新 Release 的 tag 与 `SHA256SUMS` 里 `libmpv-2.dll` 的值一起更新到这两项
- SMTC 桥接 DLL 在流水线里现编（`-PrequireSmtc=true`），编译失败会让发版直接失败
- 安装包暂未做代码签名，用户首次运行会看到 SmartScreen 的“未知发布者”提示，需选择“仍要运行”
- Android 应用内更新只取 Release 里 `.apk` 结尾的附件，桌面端附件不影响其更新检查

## 注意

- `melodia-release.jks` 一旦丢失，已上架的应用无法再发布更新，务必单独备份。
- 不要把密码提交到仓库、写进 CI 日志，或放入 `gradle.properties`（该文件被跟踪）。
