package cn.iocoder.yudao.module.channel.service.ai;

import cn.hutool.core.util.StrUtil;

/**
 * 向量的序列化与余弦相似度。知识库条目通常只有几十到几百条，在内存里逐条计算即可。
 */
public final class VectorMath {

    private VectorMath() {
    }

    public static String toJson(float[] v) {
        StringBuilder sb = new StringBuilder(v.length * 10).append('[');
        for (int i = 0; i < v.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(v[i]);
        }
        return sb.append(']').toString();
    }

    public static float[] parse(String json) {
        if (StrUtil.isBlank(json)) {
            return null;
        }
        String s = json.trim();
        s = s.substring(s.indexOf('[') + 1, s.lastIndexOf(']'));
        if (s.isBlank()) {
            return null;
        }
        String[] parts = s.split(",");
        float[] v = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            v[i] = Float.parseFloat(parts[i].trim());
        }
        return v;
    }

    public static double cosine(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length || a.length == 0) {
            return 0;
        }
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        return na == 0 || nb == 0 ? 0 : dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

}
