package cn.iocoder.yudao.framework.web.core.filter;

import cn.hutool.core.util.IdUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.regex.Pattern;

/**
 * 请求编号：取网关（Caddy）传来的 X-Request-ID，没有时自己生成；放进日志上下文（日志里的 [rid]），
 * 并在响应头里返回。用户报错时把页面上显示的请求编号发过来，就能在日志里找到这次请求。
 */
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-ID";
    public static final String MDC_KEY = "rid";
    private static final Pattern VALID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String id = request.getHeader(HEADER);
        if (id == null || !VALID.matcher(id).matches()) {
            id = IdUtil.fastSimpleUUID();
            response.setHeader(HEADER, id); // 网关已经带了编号时由网关回写响应头，这里不重复写
        }
        MDC.put(MDC_KEY, id);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    /** 当前请求的编号；不在请求里时为 null */
    public static String current() {
        return MDC.get(MDC_KEY);
    }

}
