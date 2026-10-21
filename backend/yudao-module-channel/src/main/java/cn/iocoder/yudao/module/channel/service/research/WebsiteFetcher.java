package cn.iocoder.yudao.module.channel.service.research;

import cn.hutool.core.util.StrUtil;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.regex.Pattern;

/**
 * 读取客户公司官网的少量公开页面（首页 + 关于/产品/联系等），供 AI 背调使用。
 * <ul>
 *     <li>只访问公网地址：拒绝内网、本机、链路本地等地址，重定向的每一跳都重新检查，防止被用来探测内部网络</li>
 *     <li>遵守 robots.txt；最多读取 {@link #MAX_PAGES} 个页面，每页只取正文的前一部分</li>
 * </ul>
 */
public class WebsiteFetcher {

    public static final int MAX_PAGES = 5;
    private static final int MAX_BYTES = 1_500_000;
    private static final int MAX_TEXT = 6000;
    private static final int MAX_REDIRECTS = 3;
    private static final String UA = "CGCH-Research/1.0 (+business background check; contact via website owner)";
    private static final Pattern INTERESTING = Pattern.compile(
            "(?i)(about|company|profile|who-we-are|our-story|product|catalog|solution|service|contact|factory|certificat|"
                    + "关于|公司|简介|产品|联系|工厂|资质)");
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER).build();

    /** 一个页面：地址、标题、正文（已截断） */
    public record Page(String url, String title, String text) {
    }

    public static class FetchException extends RuntimeException {
        public FetchException(String message) {
            super(message);
        }
    }

    /** 允许访问的内网主机（仅用于自动化测试，生产保持为空） */
    private final Set<String> allowedPrivateHosts;

    public WebsiteFetcher(Collection<String> allowedPrivateHosts) {
        this.allowedPrivateHosts = new HashSet<>();
        allowedPrivateHosts.forEach(h -> this.allowedPrivateHosts.add(h.trim().toLowerCase(Locale.ROOT)));
    }

    /** 规范化用户输入：补上 https://，只保留协议和主机（含端口）作为站点根 */
    public static URI normalize(String input) {
        String s = StrUtil.trim(input);
        if (StrUtil.isBlank(s)) {
            throw new FetchException("请填写官网地址");
        }
        if (!s.matches("(?i)^https?://.*")) {
            s = "https://" + s;
        }
        try {
            URI u = new URI(s);
            if (StrUtil.isBlank(u.getHost())) {
                throw new FetchException("官网地址不正确：" + input);
            }
            return new URI(u.getScheme().toLowerCase(Locale.ROOT), null, u.getHost().toLowerCase(Locale.ROOT), u.getPort(), "/", null, null);
        } catch (URISyntaxException e) {
            throw new FetchException("官网地址不正确：" + input);
        }
    }

    /** https 连不上时（不少小企业官网只有 http）改用 http 再试一次；内网地址等安全拦截不重试 */
    public List<Page> fetchSiteWithFallback(URI root) {
        try {
            return fetchSite(root);
        } catch (FetchException e) {
            if (!"https".equals(root.getScheme()) || e.getMessage().startsWith("不能访问") || e.getMessage().contains("robots.txt")) {
                throw e;
            }
            try {
                return fetchSite(new URI("http", null, root.getHost(), root.getPort(), "/", null, null));
            } catch (URISyntaxException ex) {
                throw e;
            } catch (FetchException ex) {
                throw e; // 报告最初的 https 错误
            }
        }
    }

    /** 读取站点：首页 + 最多 MAX_PAGES-1 个关于/产品/联系类的站内页面 */
    public List<Page> fetchSite(URI root) {
        Robots robots = robots(root);
        List<Page> pages = new ArrayList<>();
        Document home = document(root, robots);
        pages.add(page(home));
        List<URI> next = new ArrayList<>();
        for (Element a : home.select("a[href]")) {
            String href = a.absUrl("href");
            if (StrUtil.isBlank(href)) {
                continue;
            }
            try {
                URI u = new URI(href.split("#")[0]);
                if (!sameSite(root, u) || !INTERESTING.matcher(u.getPath() + " " + a.text()).find()) {
                    continue;
                }
                if (next.stream().noneMatch(x -> x.equals(u)) && !u.equals(URI.create(home.location()))) {
                    next.add(u);
                }
            } catch (URISyntaxException ignored) {
                // 跳过不规范的链接
            }
        }
        for (URI u : next) {
            if (pages.size() >= MAX_PAGES) {
                break;
            }
            if (!robots.allowed(u.getRawPath())) {
                continue;
            }
            try {
                pages.add(page(document(u, robots)));
            } catch (FetchException ignored) {
                // 单个子页面失败不影响整体
            }
        }
        return pages;
    }

    private static boolean sameSite(URI root, URI u) {
        String h = StrUtil.nullToEmpty(u.getHost()).toLowerCase(Locale.ROOT);
        String r = root.getHost();
        return ("http".equalsIgnoreCase(u.getScheme()) || "https".equalsIgnoreCase(u.getScheme()))
                && (h.equals(r) || h.equals("www." + r) || ("www." + h).equals(r)) && u.getPort() == root.getPort();
    }

    private static Page page(Document d) {
        Document clean = d.clone();
        clean.select("script,style,noscript,svg,iframe,form").remove();
        String text = clean.body() == null ? "" : clean.body().text();
        return new Page(d.location(), StrUtil.maxLength(d.title(), 200), StrUtil.maxLength(text, MAX_TEXT));
    }

    private Document document(URI uri, Robots robots) {
        URI current = uri;
        for (int i = 0; i <= MAX_REDIRECTS; i++) {
            checkPublic(current);
            if (!robots.allowed(StrUtil.blankToDefault(current.getRawPath(), "/"))) {
                throw new FetchException("该网站的 robots.txt 不允许访问 " + current.getPath());
            }
            HttpResponse<byte[]> resp = get(current);
            int code = resp.statusCode();
            if (code >= 300 && code < 400) {
                String loc = resp.headers().firstValue("Location").orElse(null);
                if (loc == null) {
                    throw new FetchException("网站重定向缺少目标地址");
                }
                current = current.resolve(loc);
                continue;
            }
            if (code >= 400) {
                throw new FetchException("网站返回 HTTP " + code + "：" + current);
            }
            String type = resp.headers().firstValue("Content-Type").orElse("text/html");
            if (!type.contains("html")) {
                throw new FetchException("不是网页：" + current);
            }
            byte[] body = resp.body();
            String html = new String(body, 0, Math.min(body.length, MAX_BYTES), charset(type));
            return Jsoup.parse(html, current.toString());
        }
        throw new FetchException("网站重定向次数过多");
    }

    private static java.nio.charset.Charset charset(String contentType) {
        int i = contentType.toLowerCase(Locale.ROOT).indexOf("charset=");
        if (i >= 0) {
            try {
                return java.nio.charset.Charset.forName(contentType.substring(i + 8).replace("\"", "").split(";")[0].trim());
            } catch (Exception ignored) {
                // 落到 UTF-8
            }
        }
        return java.nio.charset.StandardCharsets.UTF_8;
    }

    private static HttpResponse<byte[]> get(URI uri) {
        try {
            return HTTP.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(15)).header("User-Agent", UA)
                    .header("Accept", "text/html,text/plain;q=0.8").GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FetchException("请求被中断");
        } catch (Exception e) {
            throw new FetchException("无法访问 " + uri.getHost() + "：" + StrUtil.blankToDefault(e.getMessage(), e.getClass().getSimpleName()));
        }
    }

    /** 只允许访问公网地址（白名单中的测试主机除外） */
    void checkPublic(URI uri) {
        if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
            throw new FetchException("只支持 http/https 网址");
        }
        String host = StrUtil.nullToEmpty(uri.getHost()).toLowerCase(Locale.ROOT);
        if (allowedPrivateHosts.contains(host)) {
            return;
        }
        InetAddress[] all;
        try {
            all = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            throw new FetchException("找不到网站：" + host);
        }
        for (InetAddress a : all) {
            if (a.isAnyLocalAddress() || a.isLoopbackAddress() || a.isLinkLocalAddress() || a.isSiteLocalAddress()
                    || a.isMulticastAddress() || isUniqueLocalV6(a) || isCarrierNat(a)) {
                throw new FetchException("不能访问内网或本机地址：" + host);
            }
        }
    }

    private static boolean isUniqueLocalV6(InetAddress a) {
        return a instanceof Inet6Address && (a.getAddress()[0] & 0xfe) == 0xfc;
    }

    private static boolean isCarrierNat(InetAddress a) {
        byte[] b = a.getAddress();
        return b.length == 4 && (b[0] & 0xff) == 100 && (b[1] & 0xc0) == 64;
    }

    // ==================== robots.txt ====================

    /** 只解析 User-agent: * 下的 Disallow / Allow 前缀规则 */
    static class Robots {
        final List<String> disallow = new ArrayList<>();
        final List<String> allow = new ArrayList<>();

        boolean allowed(String path) {
            String p = StrUtil.blankToDefault(path, "/");
            int dis = disallow.stream().filter(p::startsWith).mapToInt(String::length).max().orElse(-1);
            int al = allow.stream().filter(p::startsWith).mapToInt(String::length).max().orElse(-1);
            return dis < 0 || al >= dis;
        }

        static Robots parse(String txt) {
            Robots r = new Robots();
            boolean mine = false;
            for (String raw : StrUtil.nullToEmpty(txt).split("\\r?\\n")) {
                String line = raw.split("#")[0].trim();
                int c = line.indexOf(':');
                if (c < 0) {
                    continue;
                }
                String k = line.substring(0, c).trim().toLowerCase(Locale.ROOT);
                String v = line.substring(c + 1).trim();
                if ("user-agent".equals(k)) {
                    mine = "*".equals(v) || v.toLowerCase(Locale.ROOT).startsWith("cgch");
                } else if (mine && "disallow".equals(k) && !v.isEmpty()) {
                    r.disallow.add(v);
                } else if (mine && "allow".equals(k) && !v.isEmpty()) {
                    r.allow.add(v);
                }
            }
            return r;
        }
    }

    private Robots robots(URI root) {
        try {
            URI u = root.resolve("/robots.txt");
            checkPublic(u);
            HttpResponse<byte[]> resp = get(u);
            if (resp.statusCode() == 200) {
                return Robots.parse(new String(resp.body(), java.nio.charset.StandardCharsets.UTF_8));
            }
        } catch (FetchException e) {
            if (e.getMessage().startsWith("不能访问")) {
                throw e;
            }
        }
        return new Robots();
    }

}
