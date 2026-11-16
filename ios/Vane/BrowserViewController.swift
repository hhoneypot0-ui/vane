import UIKit
import WebKit

/// iOS 骨架：一个 WKWebView + 工具条 + 主页。
/// 代理没有做：iOS 上要走 NetworkExtension（需 App Store 审核与专用权限），
/// 现有实现只保留浏览器部分，详见 docs/PLATFORMS.md。
final class BrowserViewController: UIViewController {
    private let web = WKWebView(frame: .zero, configuration: BrowserViewController.config())
    private let bar = UIToolbar()
    private let address = UITextField()

    static func config() -> WKWebViewConfiguration {
        let c = WKWebViewConfiguration()
        c.allowsInlineMediaPlayback = true
        c.mediaTypesRequiringUserActionForPlayback = []
        let ucc = WKUserContentController()
        // 与 Android 端一致：长按链接回传、视频嗅探钩子（这里只做占位）
        let js = """
        (function(){
          if (window.__vaneLP) return; window.__vaneLP = 1;
          document.addEventListener('contextmenu', function(e){
            var a = e.target && e.target.closest ? e.target.closest('a') : null;
            if (a && a.href) window.webkit.messageHandlers.vane.postMessage({type:'link', url:a.href});
          }, true);
        })();
        """
        ucc.addUserScript(WKUserScript(source: js, injectionTime: .atDocumentEnd, forMainFrameOnly: false))
        c.userContentController = ucc
        return c
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .systemBackground

        address.borderStyle = .roundedRect
        address.placeholder = "搜索或输入网址"
        address.returnKeyType = .go
        address.delegate = self
        address.translatesAutoresizingMaskIntoConstraints = false

        web.navigationDelegate = self
        web.translatesAutoresizingMaskIntoConstraints = false

        view.addSubview(address)
        view.addSubview(web)
        view.addSubview(bar)

        NSLayoutConstraint.activate([
            address.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 8),
            address.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 12),
            address.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -12),
            address.heightAnchor.constraint(equalToConstant: 36),

            web.topAnchor.constraint(equalTo: address.bottomAnchor, constant: 8),
            web.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            web.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            web.bottomAnchor.constraint(equalTo: view.bottomAnchor)
        ])

        loadHome()
    }

    private func loadHome() {
        if let url = Bundle.main.url(forResource: "home", withExtension: "html") {
            web.loadFileURL(url, allowingReadAccessTo: url.deletingLastPathComponent())
        } else {
            web.load(URLRequest(url: URL(string: "about:blank")!))
        }
    }

    private func open(_ text: String) {
        let t = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !t.isEmpty else { return }
        let url: URL?
        if t.contains("://") { url = URL(string: t) }
        else if t.contains(".") && !t.contains(" ") { url = URL(string: "https://" + t) }
        else { url = URL(string: "https://www.bing.com/search?q=" + (t.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? "")) }
        if let u = url { web.load(URLRequest(url: u)) }
    }
}

extension BrowserViewController: UITextFieldDelegate {
    func textFieldShouldReturn(_ textField: UITextField) -> Bool {
        open(textField.text ?? "")
        textField.resignFirstResponder()
        return true
    }
}

extension BrowserViewController: WKNavigationDelegate {
    func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
        address.text = webView.url?.absoluteString
    }
}
