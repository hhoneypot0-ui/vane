import NetworkExtension

/// iOS 上代理必须走 NetworkExtension。这里只放骨架：
/// 真正的实现需要把代理核心编译成 iOS 静态库（或 framework），由本扩展启动，
/// 并把流量导向本地监听端口。需要付费开发者账号 + NetworkExtension entitlement。
final class PacketTunnelProvider: NEPacketTunnelProvider {

    override func startTunnel(options: [String: NSObject]?,
                              completionHandler: @escaping (Error?) -> Void) {
        // TODO: 读取 App Group 里的订阅/节点配置 → 启动本地代理 → 设置 TUN 路由
        let settings = NEPacketTunnelNetworkSettings(tunnelRemoteAddress: "127.0.0.1")
        let ipv4 = NEIPv4Settings(addresses: ["10.8.0.2"], subnetMasks: ["255.255.255.0"])
        ipv4.includedRoutes = [NEIPv4Route.default()]
        settings.ipv4Settings = ipv4
        let dns = NEDNSSettings(servers: ["1.1.1.1", "8.8.8.8"])
        settings.dnsSettings = dns
        settings.mtu = 1400
        setTunnelNetworkSettings(settings) { error in
            completionHandler(error)
        }
    }

    override func stopTunnel(with reason: NEProviderStopReason,
                             completionHandler: @escaping () -> Void) {
        completionHandler()
    }
}
