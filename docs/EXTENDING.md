# 怎么改（给你的操作手册）

## 加一个搜索引擎
界面：设置 → 添加自定义搜索引擎 → 填名称和模板（`https://xxx/search?q=%s`）。
代码：`core/SearchEngines.java` 的 `builtin()` 里加一行：

```java
l.add(new SearchEngine("myeng", "我的引擎", "自定义", "M",
        "https://example.com/search?q=%s", null));
```

## 加一个按键功能
1. `core/Actions.java` 里加：
```java
reg("myAction", "我的功能", "★", "工具", a -> a.myFeature());
```
2. `MainActivity` 里实现 `public void myFeature() { ... }`。
3. 界面 → 按键与手势编辑器 → 选这个动作绑到任意键。

## 改底栏默认布局
改 `UiConfig.defaults()` 里的 `items`；或直接在界面编辑器里点（会覆盖默认值）。

## 改主题
- 只换配色：设置 → 强调色；`ui/Theme.java` 里的 `bg/fg/field/line` 就是全部色板。
- 加新主题模式：`Theme.isDark()` 加一个分支，`SettingsActivity.pickTheme()` 加一项。

## 换广告规则 / 分流规则
- 广告：替换 `assets/hosts.txt`，或把自定义放 `files/rules/hosts.txt`（App 私有目录）。
- 国内域名：`assets/geosite_cn.txt`，一行一个后缀（`bilibili.com` 会覆盖 `www.bilibili.com`）。
- 国内 IP：`assets/geoip_cn.txt`，CIDR 一行一个。想更准就换成完整 geoip 表。
- 单个域名强制走哪边：设置里的用户规则（`rules_proxy` / `rules_direct`），逗号分隔。

## 让 vmess/trojan 真正连通
1. 下载对应 ABI 的 `sing-box`（arm64-v8a 最常见）；
2. 放到 `app/src/main/assets/bin/arm64-v8a/sing-box`；
3. 重新打包。首次启动 `ProxyCore.installBinary()` 会复制到 `files/core/` 并 `chmod 755`，
   然后按 `CoreConfig.build()` 生成的配置拉起。

## 加一个内置页面（书签页、下载页这种）
1. 新建 `BookmarksActivity extends AppCompatActivity`；
2. `MainActivity.showBookmarks()` 从弹窗改成 `startActivity(...)`；
3. 想让它出现在菜单里：`Actions` 加一个动作即可。

## 调试代理
- 界面：订阅与节点页底部有实时日志，每条请求都标了 `→ 直连` / `→ 代理`。
- 命令行：`adb logcat -s VaneProxy` 能看到代理注入是否生效。

## 加一个新协议
1. `proxy/SubscriptionParser.java`：加一个 `case "xxx": return parseUri(link, "xxx")`，在 `parseUri` 里把需要的 query 参数读进 `Node`。
2. `proxy/Node.java`：缺什么字段就加，记得在 `toJson/fromJson` 里持久化。
3. `proxy/CoreConfig.java`：在 `outbound()` 里加一个 `case "xxx"`，按 sing-box 的字段名产出出站（字段名先在 `sing-box check` 里试，别猜）。
4. `tools/parsertest`：加解析断言；`tools/kernel-test`：加 `GenCfg` 的节点 + 服务端配置 + `e2e` 一行。
5. 跑三套自测，全绿再打包。

## 加一个新的 ABI 内核
```bash
# 例：x86_64（模拟器）
cp sing-box-<ver>-android-amd64/sing-box app/src/main/jniLibs/x86_64/libsingbox.so
# app/build.gradle 的 splits.abi.include 里加上 'x86_64'
```
`ProxyCore.binary()` 会自动用 `nativeLibraryDir/libsingbox.so`，不需要改代码。

## 测试脚本注意
`tools/kernel-test` 会起真实 sing-box 进程。跑之前确保没有残留（`pkill -x sing-box`），
否则端口被占会导致服务端起不来、测试结果被旧进程污染。
