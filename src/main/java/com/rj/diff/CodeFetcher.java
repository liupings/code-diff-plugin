package com.rj.diff;

import cn.hutool.http.HttpUtil;

public class CodeFetcher {
    public String fetchCodeFromUrl(String url) {
        return HttpUtil.get(url);
    }
}
