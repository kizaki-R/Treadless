package com.kizakiworks.glassui

/**
 * 藥丸透鏡 shader（AGSL，Android 13+）。掛在「內容層」（分頁鍵 Row／分段標籤 Row）上。
 *
 * 藥丸範圍（uCenter/uHalf 每幀由 graphicsLayer 餵）內的內容做厚玻璃透鏡：
 * - 各向異性放大（橫向 uZoom、縱向 0.45×）——由邊到心遞增，邊緣帶自然形成拉伸塗抹
 * - rim 帶 RGB 各採不同放大率 → 內容跨過玻璃邊緣時真色散（不是假的橘藍疊層）
 * - 藥丸外原樣通過；靜止時 uZoom 很小、移動時全開
 *
 * **吃的是元件自己的內容，不是背景**——所以用它不需要 Haze 那套背景模糊。
 * 全 App 共用這一份，別再各留一份。
 */
const val PILL_LENS_SHADER = """
    uniform shader content;
    uniform float2 uCenter;
    uniform float2 uHalf;
    uniform float uZoom;
    uniform float uChroma;

    float sdRoundRect(float2 p, float2 b, float r) {
        float2 q = abs(p) - b + r;
        return length(max(q, float2(0.0))) + min(max(q.x, q.y), 0.0) - r;
    }

    half4 main(float2 fragCoord) {
        float2 p = fragCoord - uCenter;
        float d = sdRoundRect(p, uHalf, uHalf.y);
        if (d >= 0.0) {
            return content.eval(fragCoord);
        }
        float band = max(uHalf.y * 0.8, 1.0);
        float t = clamp(-d / band, 0.0, 1.0);
        float m = smoothstep(0.0, 1.0, t);
        float zx = 1.0 + uZoom * m;
        float zy = 1.0 + uZoom * 0.45 * m;
        float rimMask = t * (1.0 - t) * 4.0;
        float ca = uChroma * rimMask * 0.09;
        float2 baseUv = uCenter + float2(p.x / zx, p.y / zy);
        half4 c;
        c.r = content.eval(uCenter + float2(p.x / (zx * (1.0 + ca)), p.y / zy)).r;
        half4 g = content.eval(baseUv);
        c.g = g.g;
        c.b = content.eval(uCenter + float2(p.x / (zx * (1.0 - ca)), p.y / zy)).b;
        c.a = g.a;
        return c;
    }
"""
