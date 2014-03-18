package org.usc.wechat.mp.sdk.cache;

import java.net.URI;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.utils.URIBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.usc.wechat.mp.sdk.util.Constant;
import org.usc.wechat.mp.sdk.util.JsonRtnUtil;
import org.usc.wechat.mp.sdk.vo.json.AccessTokenJsonRtn;
import org.usc.wechat.mp.sdk.vo.token.DelayItem;
import org.usc.wechat.mp.sdk.vo.token.GrantType;
import org.usc.wechat.mp.sdk.vo.token.License;

import com.alibaba.fastjson.JSONObject;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.primitives.Ints;

public class AccessTokenCache {
    private final static Logger log = LoggerFactory.getLogger(AccessTokenCache.class);

    private final static DelayQueue<DelayItem<License>> queue = new DelayQueue<DelayItem<License>>();
    private final static LoadingCache<License, String> cache = CacheBuilder.newBuilder().build(new CacheLoader<License, String>() {
        @Override
        public String load(License license) throws Exception {
            URI uri = new URIBuilder(Constant.WECHAT_TOKEN_URL)
                    .setParameter("grant_type", GrantType.CLIENT_CREDENTIAL.getValue())
                    .setParameter("appid", license.getAppId())
                    .setParameter("secret", license.getAppSecret())
                    .build();
            log.info("get access token for {}, url = {}", license, uri);

            String json = Request.Get(uri).execute().returnContent().asString();
            log.info("get access token for {}, rtn = {}", license, json);

            AccessTokenJsonRtn rtn = JSONObject.parseObject(json, AccessTokenJsonRtn.class);
            if (rtn == null) {
                log.info("parse return json msg failed when get access token for {}, rtn = {}", license, json);
                return null;
            }

            JsonRtnUtil.appendErrorHumanMsg(rtn);

            if (StringUtils.isNotEmpty(rtn.getErrCode()) && !StringUtils.equals(Constant.WECHAT_JSON_RTN_SUCCESS_CODE, rtn.getErrCode())) {
                log.info("unsuccessfully get access token for {}, rtn = {}", license, rtn);
                return null;
            }

            // for safe, expiresSeconds - 60s
            int expiresSeconds = Ints.max(rtn.getExpiresIn() - 60, 0);
            queue.put(new DelayItem<License>(license, TimeUnit.NANOSECONDS.convert(expiresSeconds, TimeUnit.SECONDS)));

            String accessToken = rtn.getAccessToken();
            log.info("successfully get access token for {}, rtn = {}", license, rtn);
            return accessToken;
        }
    });

    static {
        Thread daemonThread = new Thread(new Runnable() {
            public void run() {
                delayCheck();
            }
        });
        daemonThread.setDaemon(true);
        daemonThread.start();
    }

    private static void delayCheck() {
        while (true) {
            try {
                DelayItem<License> delayItem = queue.take();
                if (delayItem != null) {
                    cache.invalidate(delayItem.getItem());// invalidate cache for expiry/delay item
                }
            } catch (InterruptedException e) {
                break;
            }
        }
    }

    public static String getAccessToken(License license) {
//        String accessToken;
//        try {
//            accessToken = cache.get(license);
//        } catch (Exception e) {
//            log.error("get access token failed for " + license, e);
//            accessToken = StringUtils.EMPTY;
//        }
//
//        if (StringUtils.isEmpty(accessToken)) {
//            // maybe throw exception when accessToken is empty,
//        }
//
//        return accessToken;

        return "_WByUPWb8VxasiQedz9pxHuSrOoHVZx7xz0IEe3cj0OdNBWV3Q-GOxk26ZFGCfLJgHCIsrMcwKCz5pnE1EYechDbUtPKAQXmhvxP4KBuroRiJWQTiDsU6HNT3hz0uPrua1FGNLxdqeJk2bs7ynIkcA";
    }

    public static void invalidate(License license) {
        if (license != null) {
            cache.invalidate(license);
        } else {
            cache.invalidateAll();
        }
    }
}
