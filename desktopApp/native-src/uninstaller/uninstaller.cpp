// Melodia 桌面端独立卸载程序：脱壳到临时目录运行，关闭运行中实例，清理 MSI/缓存/用户数据与安装目录
#include <winsock2.h>
#include <ws2tcpip.h>
#include <windows.h>
#include <dwmapi.h>
#include <msi.h>
#include <objidl.h>
#include <gdiplus.h>
#include <shellapi.h>
#include <shlobj.h>
#include <tlhelp32.h>

#include <algorithm>
#include <atomic>
#include <cstdint>
#include <filesystem>
#include <fstream>
#include <mutex>
#include <string>
#include <thread>
#include <vector>

namespace fs = std::filesystem;

namespace {

constexpr int IDI_APP_ICON = 101;
constexpr UINT WM_UNINSTALL_PROGRESS = WM_APP + 1;
constexpr UINT WM_UNINSTALL_DONE = WM_APP + 2;

// 与 build.gradle.kts 中 upgradeUuid 保持一致
constexpr wchar_t kUpgradeCode[] = L"{6f0b8a52-3c1e-4d7a-9b25-8e4c2f1d7a93}";
constexpr wchar_t kRunRegKey[] = L"Software\\Microsoft\\Windows\\CurrentVersion\\Run";
constexpr wchar_t kUninstallRegKey[] = L"Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\Melodia";
constexpr wchar_t kVendorRegKey[] = L"Software\\Melodia";
constexpr wchar_t kAppName[] = L"Melodia";
constexpr wchar_t kCacheSubdir[] = L"MelodiaCache";

const wchar_t* const kCacheFolderNames[] = {
    L"audio_cache",
    L"meta_cache",
    L"image_cache",
    L"lyrics",
    L"local_covers",
    L"download_tmp",
};

const wchar_t* const kAppDirEntries[] = {
    L"Melodia.exe",
    L"Melodia.ico",
    L"uninstall.exe",
    L"app",
    L"runtime",
    L"cache",
};

enum class UiPhase {
    Confirm,
    Running,
    Done,
};

struct AppState {
    HWND hwnd = nullptr;
    HICON iconLarge = nullptr;
    HICON iconSmall = nullptr;
    bool darkMode = false;
    UiPhase phase = UiPhase::Confirm;
    int progressPercent = 0;
    std::wstring statusText = L"准备就绪";
    std::mutex statusMutex;
    fs::path appDir;
    int hoveredButton = 0;  // 0 无，1 取消，2 主按钮
    int pressedButton = 0;
    std::jthread worker;
};

fs::path GetModulePath() {
    std::wstring buf(MAX_PATH, L'\0');
    while (true) {
        DWORD len = GetModuleFileNameW(nullptr, buf.data(), static_cast<DWORD>(buf.size()));
        if (len == 0) return {};
        if (len < buf.size() - 1) {
            buf.resize(len);
            return fs::path(buf);
        }
        buf.resize(buf.size() * 2);
    }
}

fs::path GetEnvPath(const wchar_t* name) {
    DWORD needed = GetEnvironmentVariableW(name, nullptr, 0);
    if (needed <= 1) return {};
    std::wstring val(needed - 1, L'\0');
    GetEnvironmentVariableW(name, val.data(), needed);
    return fs::path(val);
}

fs::path GetAppDataMelodia() {
    fs::path base = GetEnvPath(L"APPDATA");
    if (base.empty()) base = GetEnvPath(L"USERPROFILE");
    return base.empty() ? fs::path() : (base / kAppName);
}

fs::path GetLocalAppDataMelodia() {
    fs::path base = GetEnvPath(L"LOCALAPPDATA");
    return base.empty() ? fs::path() : (base / kAppName);
}

fs::path NormalizePath(const fs::path& p) {
    if (p.empty()) return {};
    std::error_code ec;
    fs::path abs = fs::weakly_canonical(p, ec);
    if (ec) abs = fs::absolute(p, ec);
    std::wstring s = abs.wstring();
    while (s.size() > 3 && (s.back() == L'\\' || s.back() == L'/')) s.pop_back();
    std::transform(s.begin(), s.end(), s.begin(), ::towlower);
    return fs::path(s);
}

bool SamePath(const fs::path& a, const fs::path& b) {
    if (a.empty() || b.empty()) return false;
    return NormalizePath(a) == NormalizePath(b);
}

bool IsSubPath(const fs::path& child, const fs::path& parent) {
    fs::path normChild = NormalizePath(child);
    fs::path normParent = NormalizePath(parent);
    if (normChild.empty() || normParent.empty()) return false;
    std::wstring c = normChild.wstring();
    std::wstring p = normParent.wstring();
    if (c == p) return true;
    if (p.back() != L'\\') p.push_back(L'\\');
    return c.rfind(p, 0) == 0;
}

std::wstring Utf8ToWide(std::string_view utf8) {
    if (utf8.empty()) return {};
    int len = MultiByteToWideChar(CP_UTF8, 0, utf8.data(), static_cast<int>(utf8.size()), nullptr, 0);
    if (len <= 0) return {};
    std::wstring out(static_cast<size_t>(len), L'\0');
    MultiByteToWideChar(CP_UTF8, 0, utf8.data(), static_cast<int>(utf8.size()), out.data(), len);
    return out;
}

// 解析 Protobuf varint
bool ReadVarint(const uint8_t*& cur, const uint8_t* end, uint64_t& value) {
    value = 0;
    int shift = 0;
    while (cur < end && shift < 64) {
        uint8_t byte = *cur++;
        value |= static_cast<uint64_t>(byte & 0x7F) << shift;
        if ((byte & 0x80) == 0) return true;
        shift += 7;
    }
    return false;
}

bool SkipField(const uint8_t*& cur, const uint8_t* end, uint32_t wireType) {
    uint64_t val = 0;
    switch (wireType) {
        case 0: return ReadVarint(cur, end, val);
        case 1:
            if (end - cur < 8) return false;
            cur += 8;
            return true;
        case 2:
            if (!ReadVarint(cur, end, val) || val > static_cast<uint64_t>(end - cur)) return false;
            cur += static_cast<size_t>(val);
            return true;
        case 5:
            if (end - cur < 4) return false;
            cur += 4;
            return true;
        default:
            return false;
    }
}

// 解析 DataStore Value 消息中的 string 字段（field 5, wire type 2）
std::string ParseValueString(const uint8_t* cur, const uint8_t* end) {
    while (cur < end) {
        uint64_t tag = 0;
        if (!ReadVarint(cur, end, tag)) break;
        uint32_t fieldNum = static_cast<uint32_t>(tag >> 3);
        uint32_t wireType = static_cast<uint32_t>(tag & 0x07);
        if (fieldNum == 5 && wireType == 2) {
            uint64_t len = 0;
            if (!ReadVarint(cur, end, len) || len > static_cast<uint64_t>(end - cur)) break;
            return std::string(reinterpret_cast<const char*>(cur), static_cast<size_t>(len));
        }
        if (!SkipField(cur, end, wireType)) break;
    }
    return {};
}

// 从 desktop_prefs.preferences_pb 提取自定义缓存目录路径
std::vector<fs::path> ExtractCustomCacheRoots(const fs::path& pbFile) {
    std::vector<fs::path> result;
    std::error_code ec;
    if (!fs::is_regular_file(pbFile, ec)) return result;

    std::ifstream in(pbFile, std::ios::binary);
    if (!in) return result;
    std::vector<uint8_t> bytes((std::istreambuf_iterator<char>(in)), std::istreambuf_iterator<char>());
    if (bytes.empty()) return result;

    const uint8_t* cur = bytes.data();
    const uint8_t* end = cur + bytes.size();
    while (cur < end) {
        uint64_t tag = 0;
        if (!ReadVarint(cur, end, tag)) break;
        uint32_t fieldNum = static_cast<uint32_t>(tag >> 3);
        uint32_t wireType = static_cast<uint32_t>(tag & 0x07);
        if (fieldNum == 1 && wireType == 2) {
            uint64_t entryLen = 0;
            if (!ReadVarint(cur, end, entryLen) || entryLen > static_cast<uint64_t>(end - cur)) break;
            const uint8_t* entryCur = cur;
            const uint8_t* entryEnd = cur + static_cast<size_t>(entryLen);
            cur = entryEnd;

            std::string key;
            std::string strVal;
            while (entryCur < entryEnd) {
                uint64_t entryTag = 0;
                if (!ReadVarint(entryCur, entryEnd, entryTag)) break;
                uint32_t eField = static_cast<uint32_t>(entryTag >> 3);
                uint32_t eWire = static_cast<uint32_t>(entryTag & 0x07);
                if (eField == 1 && eWire == 2) {
                    uint64_t kLen = 0;
                    if (!ReadVarint(entryCur, entryEnd, kLen) || kLen > static_cast<uint64_t>(entryEnd - entryCur)) break;
                    key.assign(reinterpret_cast<const char*>(entryCur), static_cast<size_t>(kLen));
                    entryCur += static_cast<size_t>(kLen);
                } else if (eField == 2 && eWire == 2) {
                    uint64_t vLen = 0;
                    if (!ReadVarint(entryCur, entryEnd, vLen) || vLen > static_cast<uint64_t>(entryEnd - entryCur)) break;
                    strVal = ParseValueString(entryCur, entryCur + static_cast<size_t>(vLen));
                    entryCur += static_cast<size_t>(vLen);
                } else {
                    if (!SkipField(entryCur, entryEnd, eWire)) break;
                }
            }
            if (key == "cache_root" && !strVal.empty()) {
                std::wstring wide = Utf8ToWide(strVal);
                if (!wide.empty()) result.push_back(fs::path(wide) / kCacheSubdir);
            } else if (key == "cache_cleanup" && !strVal.empty()) {
                std::wstring wide = Utf8ToWide(strVal);
                if (!wide.empty()) {
                    fs::path cleanupPath(wide);
                    if (_wcsicmp(cleanupPath.filename().c_str(), kCacheSubdir) == 0) {
                        result.push_back(cleanupPath);
                    }
                }
            }
        } else {
            if (!SkipField(cur, end, wireType)) break;
        }
    }
    return result;
}

// 通过 SingleInstance 端口请求主程序优雅退出
bool SendQuitToSingleInstance(const fs::path& dataDir) {
    fs::path portFile = dataDir / L"instance.port";
    std::ifstream in(portFile);
    if (!in) return false;
    int port = 0;
    in >> port;
    if (port <= 0 || port > 65535) return false;

    WSADATA wsa{};
    if (WSAStartup(MAKEWORD(2, 2), &wsa) != 0) return false;
    bool ok = false;
    SOCKET sock = socket(AF_INET, SOCK_STREAM, IPPROTO_TCP);
    if (sock != INVALID_SOCKET) {
        DWORD timeout = 800;
        setsockopt(sock, SOL_SOCKET, SO_RCVTIMEO, reinterpret_cast<const char*>(&timeout), sizeof(timeout));
        setsockopt(sock, SOL_SOCKET, SO_SNDTIMEO, reinterpret_cast<const char*>(&timeout), sizeof(timeout));

        sockaddr_in addr{};
        addr.sin_family = AF_INET;
        addr.sin_port = htons(static_cast<u_short>(port));
        addr.sin_addr.s_addr = htonl(INADDR_LOOPBACK);
        if (connect(sock, reinterpret_cast<sockaddr*>(&addr), sizeof(addr)) == 0) {
            const char cmd[] = "QUIT\n";
            send(sock, cmd, sizeof(cmd) - 1, 0);
            char buf[16]{};
            int n = recv(sock, buf, sizeof(buf) - 1, 0);
            if (n >= 2 && buf[0] == 'O' && buf[1] == 'K') ok = true;
        }
        closesocket(sock);
    }
    WSACleanup();
    return ok;
}

std::vector<DWORD> FindMelodiaProcesses() {
    std::vector<DWORD> pids;
    HANDLE snap = CreateToolhelp32Snapshot(TH32CS_SNAPPROCESS, 0);
    if (snap == INVALID_HANDLE_VALUE) return pids;
    PROCESSENTRY32W pe{};
    pe.dwSize = sizeof(pe);
    DWORD selfPid = GetCurrentProcessId();
    if (Process32FirstW(snap, &pe)) {
        do {
            if (pe.th32ProcessID != selfPid && _wcsicmp(pe.szExeFile, L"Melodia.exe") == 0) {
                pids.push_back(pe.th32ProcessID);
            }
        } while (Process32NextW(snap, &pe));
    }
    CloseHandle(snap);
    return pids;
}

BOOL CALLBACK CloseProcessWindowsProc(HWND hwnd, LPARAM lParam) {
    DWORD targetPid = static_cast<DWORD>(lParam);
    DWORD winPid = 0;
    GetWindowThreadProcessId(hwnd, &winPid);
    if (winPid == targetPid) {
        PostMessageW(hwnd, WM_CLOSE, 0, 0);
    }
    return TRUE;
}

void StopRunningMelodia(const fs::path& dataDir) {
    if (FindMelodiaProcesses().empty()) return;

    SendQuitToSingleInstance(dataDir);
    for (int i = 0; i < 25; ++i) {
        if (FindMelodiaProcesses().empty()) return;
        Sleep(100);
    }

    auto pids = FindMelodiaProcesses();
    for (DWORD pid : pids) {
        EnumWindows(CloseProcessWindowsProc, static_cast<LPARAM>(pid));
    }
    for (int i = 0; i < 15; ++i) {
        if (FindMelodiaProcesses().empty()) return;
        Sleep(100);
    }

    pids = FindMelodiaProcesses();
    for (DWORD pid : pids) {
        HANDLE hProc = OpenProcess(PROCESS_TERMINATE | SYNCHRONIZE, FALSE, pid);
        if (hProc) {
            TerminateProcess(hProc, 0);
            WaitForSingleObject(hProc, 2000);
            CloseHandle(hProc);
        }
    }
}

void RemoveRegistryEntries() {
    HKEY hKey = nullptr;
    if (RegOpenKeyExW(HKEY_CURRENT_USER, kRunRegKey, 0, KEY_SET_VALUE, &hKey) == ERROR_SUCCESS) {
        RegDeleteValueW(hKey, kAppName);
        RegCloseKey(hKey);
    }
    RegDeleteTreeW(HKEY_CURRENT_USER, kUninstallRegKey);
    RegDeleteTreeW(HKEY_CURRENT_USER, kVendorRegKey);
}

bool RunProcessAndWait(std::wstring cmdLine, DWORD timeoutMs = 120000) {
    STARTUPINFOW si{};
    si.cb = sizeof(si);
    si.dwFlags = STARTF_USESHOWWINDOW;
    si.wShowWindow = SW_HIDE;
    PROCESS_INFORMATION pi{};
    if (!CreateProcessW(nullptr, cmdLine.data(), nullptr, nullptr, FALSE, CREATE_NO_WINDOW, nullptr, nullptr, &si, &pi)) {
        return false;
    }
    WaitForSingleObject(pi.hProcess, timeoutMs);
    CloseHandle(pi.hThread);
    CloseHandle(pi.hProcess);
    return true;
}

// 查找并卸载已安装的 Melodia MSI 包
void UninstallMsiPackage(const fs::path& appDir) {
    wchar_t productCode[39]{};
    for (DWORD idx = 0; MsiEnumRelatedProductsW(kUpgradeCode, 0, idx, productCode) == ERROR_SUCCESS; ++idx) {
        wchar_t installLoc[MAX_PATH]{};
        DWORD len = MAX_PATH;
        MsiGetProductInfoW(productCode, INSTALLPROPERTY_INSTALLLOCATION, installLoc, &len);
        fs::path msiDir = installLoc[0] ? fs::path(installLoc) : GetLocalAppDataMelodia();

        // 当前卸载目标是 MSI 安装目录（或 MSI 目录已不存在）时执行 MSI 卸载
        std::error_code ec;
        if (appDir.empty() || SamePath(appDir, msiDir) || !fs::exists(msiDir, ec)) {
            std::wstring cmd = L"msiexec.exe /x " + std::wstring(productCode) + L" /qn REBOOT=ReallySuppress";
            RunProcessAndWait(cmd);
            RegDeleteTreeW(HKEY_CURRENT_USER, kUninstallRegKey);
            RegDeleteTreeW(HKEY_CURRENT_USER, kVendorRegKey);
        }
    }
}

void RemoveTreeWithRetry(const fs::path& target) {
    if (target.empty()) return;
    std::error_code ec;
    if (!fs::exists(target, ec)) return;

    for (int attempt = 0; attempt < 6; ++attempt) {
        ec.clear();
        fs::remove_all(target, ec);
        if (!fs::exists(target, ec)) return;
        Sleep(150);
    }
}

void RemoveCacheDirectories(const std::vector<fs::path>& customCacheRoots) {
    // 自定义缓存目录只清理已知缓存子目录，空目录才移除父文件夹，避免误伤其他文件
    for (const fs::path& root : customCacheRoots) {
        if (_wcsicmp(root.filename().c_str(), kCacheSubdir) != 0) continue;
        std::error_code ec;
        if (!fs::is_directory(root, ec)) continue;
        for (const wchar_t* sub : kCacheFolderNames) {
            RemoveTreeWithRetry(root / sub);
        }
        RemoveDirectoryW(root.c_str());
    }

    fs::path localMelodia = GetLocalAppDataMelodia();
    if (!localMelodia.empty()) {
        RemoveTreeWithRetry(localMelodia / L"cache");
        RemoveDirectoryW(localMelodia.c_str());
    }
}

void RemoveAppDirectory(const fs::path& appDir) {
    if (appDir.empty()) return;
    std::error_code ec;
    if (!fs::is_directory(appDir, ec)) return;

    // 校验目录特征，仅移除 Melodia 自身文件与子目录
    bool isMelodiaDir = (_wcsicmp(appDir.filename().c_str(), kAppName) == 0) ||
                        fs::exists(appDir / L"Melodia.exe", ec) ||
                        fs::exists(appDir / L"app" / L".jpackage.xml", ec);
    if (!isMelodiaDir) return;

    for (const wchar_t* entry : kAppDirEntries) {
        RemoveTreeWithRetry(appDir / entry);
    }
    RemoveDirectoryW(appDir.c_str());

    fs::path localMelodia = GetLocalAppDataMelodia();
    if (!localMelodia.empty()) {
        RemoveDirectoryW(localMelodia.c_str());
    }
}

void ExecuteFullUninstall(AppState* state) {
    auto report = [state](int percent, const wchar_t* text) {
        if (!state || !state->hwnd) return;
        {
            std::lock_guard<std::mutex> lock(state->statusMutex);
            state->progressPercent = percent;
            state->statusText = text;
        }
        PostMessageW(state->hwnd, WM_UNINSTALL_PROGRESS, 0, 0);
    };

    fs::path dataDir = GetAppDataMelodia();
    fs::path prefsFile = dataDir / L"datastore" / L"desktop_prefs.preferences_pb";
    std::vector<fs::path> customCaches = ExtractCustomCacheRoots(prefsFile);

    report(20, L"正在关闭运行中的 Melodia...");
    StopRunningMelodia(dataDir);

    report(45, L"正在移除系统组件与开机自启项...");
    RemoveRegistryEntries();
    UninstallMsiPackage(state ? state->appDir : fs::path());

    report(75, L"正在清理缓存与用户配置数据...");
    RemoveCacheDirectories(customCaches);
    RemoveTreeWithRetry(dataDir);

    report(92, L"正在清理应用程序文件...");
    if (state) {
        RemoveAppDirectory(state->appDir);
    }

    report(100, L"卸载完成");
    if (state && state->hwnd) {
        PostMessageW(state->hwnd, WM_UNINSTALL_DONE, 0, 0);
    }
}

bool IsSystemDarkMode() {
    DWORD useLight = 1;
    DWORD size = sizeof(useLight);
    if (RegGetValueW(HKEY_CURRENT_USER,
                     L"Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize",
                     L"AppsUseLightTheme",
                     RRF_RT_REG_DWORD,
                     nullptr,
                     &useLight,
                     &size) == ERROR_SUCCESS) {
        return useLight == 0;
    }
    return false;
}

void ApplyWindowTheme(HWND hwnd, bool dark) {
    BOOL darkVal = dark ? TRUE : FALSE;
    // DWMWA_USE_IMMERSIVE_DARK_MODE = 20, DWMWA_WINDOW_CORNER_PREFERENCE = 33 (DWMWCP_ROUND = 2)
    DwmSetWindowAttribute(hwnd, 20, &darkVal, sizeof(darkVal));
    DWORD corner = 2;
    DwmSetWindowAttribute(hwnd, 33, &corner, sizeof(corner));
}

float GetScale(HWND hwnd) {
    UINT dpi = GetDpiForWindow(hwnd);
    return dpi > 0 ? static_cast<float>(dpi) / 96.0f : 1.0f;
}

Gdiplus::RectF GetSecondaryButtonRect(float width, float height, float scale) {
    float btnW = 88.0f * scale;
    float btnH = 34.0f * scale;
    float margin = 24.0f * scale;
    float gap = 12.0f * scale;
    return Gdiplus::RectF(width - margin - btnW * 2.0f - gap, height - margin - btnH, btnW, btnH);
}

Gdiplus::RectF GetPrimaryButtonRect(float width, float height, float scale) {
    float btnW = 96.0f * scale;
    float btnH = 34.0f * scale;
    float margin = 24.0f * scale;
    return Gdiplus::RectF(width - margin - btnW, height - margin - btnH, btnW, btnH);
}

void AddRoundedRect(Gdiplus::GraphicsPath& path, const Gdiplus::RectF& r, float radius) {
    float d = radius * 2.0f;
    path.AddArc(r.X, r.Y, d, d, 180.0f, 90.0f);
    path.AddArc(r.GetRight() - d, r.Y, d, d, 270.0f, 90.0f);
    path.AddArc(r.GetRight() - d, r.GetBottom() - d, d, d, 0.0f, 90.0f);
    path.AddArc(r.X, r.GetBottom() - d, d, d, 90.0f, 90.0f);
    path.CloseFigure();
}

void FillRoundRect(Gdiplus::Graphics& g, const Gdiplus::RectF& r, float radius, const Gdiplus::Color& color) {
    Gdiplus::GraphicsPath path;
    AddRoundedRect(path, r, radius);
    Gdiplus::SolidBrush brush(color);
    g.FillPath(&brush, &path);
}

void DrawButton(Gdiplus::Graphics& g,
                const Gdiplus::RectF& rect,
                const wchar_t* text,
                float scale,
                const Gdiplus::Color& bg,
                const Gdiplus::Color& fg) {
    FillRoundRect(g, rect, 6.0f * scale, bg);
    Gdiplus::FontFamily family(L"Microsoft YaHei UI");
    Gdiplus::Font font(&family, 9.5f * scale, Gdiplus::FontStyleRegular, Gdiplus::UnitPixel);
    Gdiplus::StringFormat fmt;
    fmt.SetAlignment(Gdiplus::StringAlignmentCenter);
    fmt.SetLineAlignment(Gdiplus::StringAlignmentCenter);
    Gdiplus::SolidBrush textBrush(fg);
    g.DrawString(text, -1, &font, rect, &fmt, &textBrush);
}

void RenderWindow(HWND hwnd, AppState* state) {
    PAINTSTRUCT ps{};
    HDC hdc = BeginPaint(hwnd, &ps);
    RECT rc{};
    GetClientRect(hwnd, &rc);
    int w = rc.right - rc.left;
    int h = rc.bottom - rc.top;

    HDC memDc = CreateCompatibleDC(hdc);
    HBITMAP memBmp = CreateCompatibleBitmap(hdc, w, h);
    HGDIOBJ oldBmp = SelectObject(memDc, memBmp);

    {
        Gdiplus::Graphics g(memDc);
        g.SetSmoothingMode(Gdiplus::SmoothingModeAntiAlias);
        g.SetTextRenderingHint(Gdiplus::TextRenderingHintClearTypeGridFit);

        float scale = GetScale(hwnd);
        float fw = static_cast<float>(w);
        float fh = static_cast<float>(h);
        bool dark = state->darkMode;

        Gdiplus::Color bgColor = dark ? Gdiplus::Color(255, 28, 28, 32) : Gdiplus::Color(255, 250, 250, 252);
        Gdiplus::Color titleColor = dark ? Gdiplus::Color(255, 244, 244, 246) : Gdiplus::Color(255, 24, 24, 28);
        Gdiplus::Color bodyColor = dark ? Gdiplus::Color(255, 175, 175, 185) : Gdiplus::Color(255, 96, 98, 108);
        Gdiplus::Color noteColor = dark ? Gdiplus::Color(255, 140, 142, 152) : Gdiplus::Color(255, 125, 128, 138);

        g.Clear(bgColor);

        int iconSize = static_cast<int>(40.0f * scale);
        int iconX = static_cast<int>(24.0f * scale);
        int iconY = static_cast<int>(24.0f * scale);
        if (state->iconLarge) {
            DrawIconEx(memDc, iconX, iconY, state->iconLarge, iconSize, iconSize, 0, nullptr, DI_NORMAL);
        }

        Gdiplus::FontFamily family(L"Microsoft YaHei UI");
        Gdiplus::Font titleFont(&family, 16.0f * scale, Gdiplus::FontStyleBold, Gdiplus::UnitPixel);
        Gdiplus::Font bodyFont(&family, 13.0f * scale, Gdiplus::FontStyleRegular, Gdiplus::UnitPixel);
        Gdiplus::Font smallFont(&family, 12.0f * scale, Gdiplus::FontStyleRegular, Gdiplus::UnitPixel);

        Gdiplus::SolidBrush titleBrush(titleColor);
        Gdiplus::SolidBrush bodyBrush(bodyColor);
        Gdiplus::SolidBrush noteBrush(noteColor);

        float contentX = 78.0f * scale;
        float contentW = fw - contentX - 24.0f * scale;

        if (state->phase == UiPhase::Confirm) {
            g.DrawString(L"卸载 Melodia", -1, &titleFont, Gdiplus::PointF(contentX, 22.0f * scale), &titleBrush);

            Gdiplus::RectF descRect(contentX, 56.0f * scale, contentW, 48.0f * scale);
            g.DrawString(L"即将从电脑中移除 Melodia 应用程序，并清除个人配置、本地曲库索引、缓存与开机自启项。",
                         -1,
                         &bodyFont,
                         descRect,
                         nullptr,
                         &bodyBrush);

            Gdiplus::RectF keepRect(contentX, 108.0f * scale, contentW, 24.0f * scale);
            g.DrawString(L"已下载的音乐文件将为您保留，不会被删除。",
                         -1,
                         &smallFont,
                         keepRect,
                         nullptr,
                         &noteBrush);

            Gdiplus::RectF cancelRect = GetSecondaryButtonRect(fw, fh, scale);
            Gdiplus::RectF okRect = GetPrimaryButtonRect(fw, fh, scale);

            Gdiplus::Color cancelBg = dark
                ? (state->hoveredButton == 1 ? Gdiplus::Color(255, 62, 62, 70) : Gdiplus::Color(255, 48, 48, 56))
                : (state->hoveredButton == 1 ? Gdiplus::Color(255, 226, 226, 232) : Gdiplus::Color(255, 236, 236, 240));
            Gdiplus::Color okBg = state->hoveredButton == 2
                ? Gdiplus::Color(255, 210, 12, 42)
                : Gdiplus::Color(255, 230, 0, 38);

            DrawButton(g, cancelRect, L"取消", 14.0f * scale / 9.5f, cancelBg, titleColor);
            DrawButton(g, okRect, L"立即卸载", 14.0f * scale / 9.5f, okBg, Gdiplus::Color(255, 255, 255, 255));
        } else if (state->phase == UiPhase::Running) {
            g.DrawString(L"正在卸载 Melodia", -1, &titleFont, Gdiplus::PointF(contentX, 22.0f * scale), &titleBrush);

            std::wstring status;
            int pct = 0;
            {
                std::lock_guard<std::mutex> lock(state->statusMutex);
                status = state->statusText;
                pct = state->progressPercent;
            }

            g.DrawString(status.c_str(), -1, &bodyFont, Gdiplus::PointF(contentX, 62.0f * scale), &bodyBrush);

            Gdiplus::RectF trackRect(contentX, 98.0f * scale, contentW, 8.0f * scale);
            Gdiplus::Color trackColor = dark ? Gdiplus::Color(255, 52, 52, 60) : Gdiplus::Color(255, 226, 226, 232);
            FillRoundRect(g, trackRect, 4.0f * scale, trackColor);

            float fillW = (std::max)(8.0f * scale, contentW * (static_cast<float>(pct) / 100.0f));
            Gdiplus::RectF fillRect(contentX, 98.0f * scale, (std::min)(contentW, fillW), 8.0f * scale);
            FillRoundRect(g, fillRect, 4.0f * scale, Gdiplus::Color(255, 230, 0, 38));
        } else {
            g.DrawString(L"卸载完成", -1, &titleFont, Gdiplus::PointF(contentX, 22.0f * scale), &titleBrush);

            Gdiplus::RectF doneRect(contentX, 60.0f * scale, contentW, 48.0f * scale);
            g.DrawString(L"Melodia 及其配置、缓存数据已从您的电脑中移除。",
                         -1,
                         &bodyFont,
                         doneRect,
                         nullptr,
                         &bodyBrush);

            Gdiplus::RectF finishRect = GetPrimaryButtonRect(fw, fh, scale);
            Gdiplus::Color finishBg = state->hoveredButton == 2
                ? Gdiplus::Color(255, 210, 12, 42)
                : Gdiplus::Color(255, 230, 0, 38);
            DrawButton(g, finishRect, L"完成", 14.0f * scale / 9.5f, finishBg, Gdiplus::Color(255, 255, 255, 255));
        }
    }

    BitBlt(hdc, 0, 0, w, h, memDc, 0, 0, SRCCOPY);
    SelectObject(memDc, oldBmp);
    DeleteObject(memBmp);
    DeleteDC(memDc);
    EndPaint(hwnd, &ps);
}

int HitTestButtons(HWND hwnd, AppState* state, int x, int y) {
    RECT rc{};
    GetClientRect(hwnd, &rc);
    float fw = static_cast<float>(rc.right - rc.left);
    float fh = static_cast<float>(rc.bottom - rc.top);
    float scale = GetScale(hwnd);
    Gdiplus::PointF pt(static_cast<float>(x), static_cast<float>(y));

    if (state->phase == UiPhase::Confirm) {
        if (GetSecondaryButtonRect(fw, fh, scale).Contains(pt)) return 1;
        if (GetPrimaryButtonRect(fw, fh, scale).Contains(pt)) return 2;
    } else if (state->phase == UiPhase::Done) {
        if (GetPrimaryButtonRect(fw, fh, scale).Contains(pt)) return 2;
    }
    return 0;
}

void StartUninstallWorker(AppState* state) {
    state->phase = UiPhase::Running;
    state->hoveredButton = 0;
    HMENU sysMenu = GetSystemMenu(state->hwnd, FALSE);
    if (sysMenu) EnableMenuItem(sysMenu, SC_CLOSE, MF_BYCOMMAND | MF_DISABLED | MF_GRAYED);
    InvalidateRect(state->hwnd, nullptr, FALSE);
    state->worker = std::jthread([state]() { ExecuteFullUninstall(state); });
}

LRESULT CALLBACK WndProc(HWND hwnd, UINT msg, WPARAM wParam, LPARAM lParam) {
    auto* state = reinterpret_cast<AppState*>(GetWindowLongPtrW(hwnd, GWLP_USERDATA));
    switch (msg) {
        case WM_CREATE: {
            auto* cs = reinterpret_cast<CREATESTRUCTW*>(lParam);
            state = reinterpret_cast<AppState*>(cs->lpCreateParams);
            state->hwnd = hwnd;
            SetWindowLongPtrW(hwnd, GWLP_USERDATA, reinterpret_cast<LONG_PTR>(state));
            ApplyWindowTheme(hwnd, state->darkMode);
            return 0;
        }
        case WM_ERASEBKGND:
            return 1;
        case WM_PAINT:
            if (state) RenderWindow(hwnd, state);
            return 0;
        case WM_MOUSEMOVE: {
            if (!state) return 0;
            int x = static_cast<short>(LOWORD(lParam));
            int y = static_cast<short>(HIWORD(lParam));
            int hit = HitTestButtons(hwnd, state, x, y);
            if (hit != state->hoveredButton) {
                state->hoveredButton = hit;
                InvalidateRect(hwnd, nullptr, FALSE);
            }
            SetCursor(LoadCursorW(nullptr, hit != 0 ? IDC_HAND : IDC_ARROW));
            return 0;
        }
        case WM_LBUTTONDOWN: {
            if (!state) return 0;
            int x = static_cast<short>(LOWORD(lParam));
            int y = static_cast<short>(HIWORD(lParam));
            state->pressedButton = HitTestButtons(hwnd, state, x, y);
            return 0;
        }
        case WM_LBUTTONUP: {
            if (!state) return 0;
            int x = static_cast<short>(LOWORD(lParam));
            int y = static_cast<short>(HIWORD(lParam));
            int hit = HitTestButtons(hwnd, state, x, y);
            int pressed = state->pressedButton;
            state->pressedButton = 0;
            if (hit != 0 && hit == pressed) {
                if (state->phase == UiPhase::Confirm) {
                    if (hit == 1) {
                        DestroyWindow(hwnd);
                    } else if (hit == 2) {
                        StartUninstallWorker(state);
                    }
                } else if (state->phase == UiPhase::Done && hit == 2) {
                    DestroyWindow(hwnd);
                }
            }
            return 0;
        }
        case WM_KEYDOWN:
            if (state && wParam == VK_ESCAPE && state->phase != UiPhase::Running) {
                DestroyWindow(hwnd);
                return 0;
            }
            break;
        case WM_UNINSTALL_PROGRESS:
            InvalidateRect(hwnd, nullptr, FALSE);
            return 0;
        case WM_UNINSTALL_DONE:
            if (state) {
                state->phase = UiPhase::Done;
                HMENU sysMenu = GetSystemMenu(hwnd, FALSE);
                if (sysMenu) EnableMenuItem(sysMenu, SC_CLOSE, MF_BYCOMMAND | MF_ENABLED);
                InvalidateRect(hwnd, nullptr, FALSE);
            }
            return 0;
        case WM_CLOSE:
            if (state && state->phase == UiPhase::Running) return 0;
            DestroyWindow(hwnd);
            return 0;
        case WM_DPICHANGED: {
            auto* suggested = reinterpret_cast<RECT*>(lParam);
            SetWindowPos(hwnd,
                         nullptr,
                         suggested->left,
                         suggested->top,
                         suggested->right - suggested->left,
                         suggested->bottom - suggested->top,
                         SWP_NOZORDER | SWP_NOACTIVATE);
            InvalidateRect(hwnd, nullptr, FALSE);
            return 0;
        }
        case WM_DESTROY:
            PostQuitMessage(0);
            return 0;
    }
    return DefWindowProcW(hwnd, msg, wParam, lParam);
}

// 若当前非 --run-from-temp，则复制自身到独立的临时目录中运行，释放对应用目录的文件占用
bool RelaunchFromTempIfNeeded(bool isFromTemp, bool silent, const fs::path& selfPath, const fs::path& appDir) {
    if (isFromTemp || selfPath.empty()) return false;
    wchar_t tempDirBuf[MAX_PATH]{};
    DWORD len = GetTempPathW(MAX_PATH, tempDirBuf);
    if (len == 0 || len >= MAX_PATH) return false;
    fs::path tempDir(tempDirBuf);

    fs::path runnerDir = tempDir / (L"melodia_uninst_" + std::to_wstring(GetCurrentProcessId()));
    std::error_code ec;
    fs::create_directories(runnerDir, ec);
    fs::path tempExe = runnerDir / L"uninstall.exe";
    fs::copy_file(selfPath, tempExe, fs::copy_options::overwrite_existing, ec);
    if (ec) return false;

    std::wstring cmd = L"\"" + tempExe.wstring() + L"\" --run-from-temp \"" + appDir.wstring() + L"\"";
    if (silent) cmd += L" --silent";

    STARTUPINFOW si{};
    si.cb = sizeof(si);
    PROCESS_INFORMATION pi{};
    if (!CreateProcessW(tempExe.c_str(),
                        cmd.data(),
                        nullptr,
                        nullptr,
                        FALSE,
                        0,
                        nullptr,
                        tempDir.c_str(),
                        &si,
                        &pi)) {
        fs::remove_all(runnerDir, ec);
        return false;
    }
    CloseHandle(pi.hThread);
    CloseHandle(pi.hProcess);
    return true;
}

void ScheduleSelfDelete(const fs::path& selfPath) {
    if (selfPath.empty()) return;
    fs::path runnerDir = selfPath.parent_path();
    std::wstring dirName = runnerDir.filename().wstring();
    if (dirName.rfind(L"melodia_uninst_", 0) != 0) return;

    wchar_t tempDirBuf[MAX_PATH]{};
    if (GetTempPathW(MAX_PATH, tempDirBuf) == 0) return;
    fs::path tempDir(tempDirBuf);
    if (!IsSubPath(runnerDir, tempDir)) return;

    std::wstring cmd = L"cmd.exe /c ping 127.0.0.1 -n 2 > nul & rmdir /s /q \"" + runnerDir.wstring() + L"\"";
    STARTUPINFOW si{};
    si.cb = sizeof(si);
    si.dwFlags = STARTF_USESHOWWINDOW;
    si.wShowWindow = SW_HIDE;
    PROCESS_INFORMATION pi{};
    if (CreateProcessW(nullptr, cmd.data(), nullptr, nullptr, FALSE, CREATE_NO_WINDOW, nullptr, tempDir.c_str(), &si, &pi)) {
        CloseHandle(pi.hThread);
        CloseHandle(pi.hProcess);
    }
}

}  // namespace

int WINAPI wWinMain(HINSTANCE hInstance, HINSTANCE, PWSTR, int nCmdShow) {
    int argc = 0;
    LPWSTR* argv = CommandLineToArgvW(GetCommandLineW(), &argc);
    bool isFromTemp = false;
    bool silent = false;
    fs::path appDir;

    if (argv) {
        for (int i = 1; i < argc; ++i) {
            if (wcscmp(argv[i], L"--run-from-temp") == 0 && i + 1 < argc) {
                isFromTemp = true;
                appDir = argv[++i];
            } else if (wcscmp(argv[i], L"--silent") == 0 || wcscmp(argv[i], L"/S") == 0 || wcscmp(argv[i], L"/quiet") == 0) {
                silent = true;
            }
        }
        LocalFree(argv);
    }

    fs::path selfPath = GetModulePath();
    if (appDir.empty() && !selfPath.empty()) {
        appDir = selfPath.parent_path();
    }

    if (RelaunchFromTempIfNeeded(isFromTemp, silent, selfPath, appDir)) {
        return 0;
    }

    // 切走工作目录，确保不锁死应用目录
    wchar_t tempDirBuf[MAX_PATH]{};
    if (GetTempPathW(MAX_PATH, tempDirBuf) > 0) {
        SetCurrentDirectoryW(tempDirBuf);
    }

    AppState state;
    state.appDir = appDir;
    state.darkMode = IsSystemDarkMode();

    if (silent) {
        ExecuteFullUninstall(&state);
        if (isFromTemp) ScheduleSelfDelete(selfPath);
        return 0;
    }

    Gdiplus::GdiplusStartupInput gdiplusInput;
    ULONG_PTR gdiplusToken = 0;
    Gdiplus::GdiplusStartup(&gdiplusToken, &gdiplusInput, nullptr);

    state.iconLarge = static_cast<HICON>(LoadImageW(hInstance, MAKEINTRESOURCEW(IDI_APP_ICON), IMAGE_ICON, 64, 64, LR_DEFAULTCOLOR));
    state.iconSmall = static_cast<HICON>(LoadImageW(hInstance, MAKEINTRESOURCEW(IDI_APP_ICON), IMAGE_ICON, 16, 16, LR_DEFAULTCOLOR));

    WNDCLASSEXW wc{};
    wc.cbSize = sizeof(wc);
    wc.lpfnWndProc = WndProc;
    wc.hInstance = hInstance;
    wc.hIcon = state.iconLarge;
    wc.hIconSm = state.iconSmall;
    wc.hCursor = LoadCursorW(nullptr, IDC_ARROW);
    wc.lpszClassName = L"MelodiaUninstallerWindow";
    RegisterClassExW(&wc);

    UINT dpi = GetDpiForSystem();
    float scale = dpi > 0 ? static_cast<float>(dpi) / 96.0f : 1.0f;
    DWORD style = WS_OVERLAPPED | WS_CAPTION | WS_SYSMENU | WS_MINIMIZEBOX;
    RECT winRect{0, 0, static_cast<LONG>(460.0f * scale), static_cast<LONG>(210.0f * scale)};
    AdjustWindowRectExForDpi(&winRect, style, FALSE, 0, dpi);
    int winW = winRect.right - winRect.left;
    int winH = winRect.bottom - winRect.top;

    RECT workArea{};
    SystemParametersInfoW(SPI_GETWORKAREA, 0, &workArea, 0);
    int posX = workArea.left + ((workArea.right - workArea.left) - winW) / 2;
    int posY = workArea.top + ((workArea.bottom - workArea.top) - winH) / 2;

    HWND hwnd = CreateWindowExW(0,
                                wc.lpszClassName,
                                L"卸载 Melodia",
                                style,
                                posX,
                                posY,
                                winW,
                                winH,
                                nullptr,
                                nullptr,
                                hInstance,
                                &state);
    if (hwnd) {
        ShowWindow(hwnd, nCmdShow);
        UpdateWindow(hwnd);
        MSG msg{};
        while (GetMessageW(&msg, nullptr, 0, 0) > 0) {
            TranslateMessage(&msg);
            DispatchMessageW(&msg);
        }
    }

    if (state.worker.joinable()) state.worker.join();
    if (state.iconLarge) DestroyIcon(state.iconLarge);
    if (state.iconSmall) DestroyIcon(state.iconSmall);
    Gdiplus::GdiplusShutdown(gdiplusToken);

    if (isFromTemp) {
        ScheduleSelfDelete(selfPath);
    }
    return 0;
}
