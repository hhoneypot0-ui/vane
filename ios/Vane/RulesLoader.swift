import WebKit

/// 把打包进来的 Rules/rules-*.json 编译成 WKContentRuleList 并挂到用户脚本控制器上。
/// 规则由 tools/ios/gen_rules.py 从 Android 端的 hosts.txt 生成（约 7.8 万个域名，切成 6 片）。
final class RulesLoader {
    static let shared = RulesLoader()
    private(set) var lists: [WKContentRuleList] = []
    private var started = false

    func attach(to controller: WKUserContentController, done: (() -> Void)? = nil) {
        guard !started else { done?(); return }
        started = true
        let names = (Bundle.main.urls(forResourcesWithExtension: "json", subdirectory: "Rules") ?? [])
            .map { $0.lastPathComponent }
            .sorted()
        guard !names.isEmpty else { done?(); return }

        let store = WKContentRuleListStore.default()
        let group = DispatchGroup()
        for name in names {
            guard let url = Bundle.main.url(forResource: name, withExtension: nil, subdirectory: "Rules"),
                  let json = try? String(contentsOf: url, encoding: .utf8) else { continue }
            let identifier = "vane-" + name
            group.enter()
            store.compileContentRuleList(forIdentifier: identifier, encodedContentRuleList: json) { list, error in
                if let list = list {
                    controller.add(list)
                    self.lists.append(list)
                } else if let error = error {
                    print("[Vane] 规则编译失败 \(name): \(error.localizedDescription)")
                }
                group.leave()
            }
        }
        group.notify(queue: .main) {
            print("[Vane] 已加载 \(self.lists.count)/\(names.count) 组拦截规则")
            done?()
        }
    }
}
