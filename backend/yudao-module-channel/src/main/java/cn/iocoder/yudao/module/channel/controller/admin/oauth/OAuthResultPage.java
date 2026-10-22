package cn.iocoder.yudao.module.channel.controller.admin.oauth;

import cn.hutool.core.util.StrUtil;
import org.springframework.web.util.HtmlUtils;

/**
 * 授权回调后展示在弹出窗口里的结果页：通知打开它的页面（postMessage）后自动关闭。
 */
public final class OAuthResultPage {

    private OAuthResultPage() {
    }

    /** @param type postMessage 的消息类型，前端据此识别 */
    public static String html(String type, boolean ok, String title, String detail) {
        String t = HtmlUtils.htmlEscape(title), d = HtmlUtils.htmlEscape(StrUtil.nullToEmpty(detail));
        return "<!doctype html><html><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width\">"
                + "<title>" + t + "</title></head><body style=\"font-family:system-ui,sans-serif;padding:48px;text-align:center\">"
                + "<h2 style=\"color:" + (ok ? "#1a7f37" : "#cf222e") + "\">" + t + "</h2><p>" + d + "</p>"
                + "<script>try{window.opener&&window.opener.postMessage({type:'" + type + "',ok:" + ok + "},'*');"
                + (ok ? "setTimeout(function(){window.close()},2500);" : "") + "}catch(e){}</script></body></html>";
    }

}
