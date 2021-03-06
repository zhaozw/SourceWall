package net.nashlegend.sourcewall.request.api;

import android.text.TextUtils;
import android.webkit.CookieManager;
import android.webkit.CookieSyncManager;

import net.nashlegend.sourcewall.App;
import net.nashlegend.sourcewall.data.Consts.Keys;
import net.nashlegend.sourcewall.data.database.BasketHelper;
import net.nashlegend.sourcewall.events.Emitter;
import net.nashlegend.sourcewall.events.LoginStateChangedEvent;
import net.nashlegend.sourcewall.fragment.PostPagerFragment;
import net.nashlegend.sourcewall.model.UserInfo;
import net.nashlegend.sourcewall.request.HttpUtil;
import net.nashlegend.sourcewall.request.NetworkTask;
import net.nashlegend.sourcewall.request.ParamsMap;
import net.nashlegend.sourcewall.request.RequestBuilder;
import net.nashlegend.sourcewall.request.RequestCallBack;
import net.nashlegend.sourcewall.request.parsers.BooleanParser;
import net.nashlegend.sourcewall.request.parsers.UserInfoParser;
import net.nashlegend.sourcewall.util.PrefsUtil;

/**
 * Created by NashLegend on 2014/11/25 0025
 */
public class UserAPI extends APIBase {

    public static boolean isLoggedIn() {
        String token = getToken();
        String ukey = getUkey();
        return !TextUtils.isEmpty(ukey) && ukey.length() == 6 && !TextUtils.isEmpty(token)
                && token.length() == 64;
    }

    public static String base36Encode(long id) {
        String ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
        id = Math.abs(id);
        StringBuilder sb = new StringBuilder();
        for (; id > 0; id /= 36) {
            sb.insert(0, ALPHABET.charAt((int) (id % 36)));
        }
        return sb.toString().toLowerCase();
    }

    /**
     * 举报
     */
    public static NetworkTask<Boolean> report(String url, String reason,
            RequestCallBack<Boolean> callBack) {
        return new RequestBuilder<Boolean>()
                .post()
                .url("http://www.guokr.com/apis/censor/report.json")
                .addParam("url", url)
                .addParam("reason", reason)
                .parser(new BooleanParser())
                .callback(callBack)
                .requestAsync();
    }

    public static String getUserInfoString() {
        return "用户名：" + UserAPI.getName() + "\n用户key：" + getUkey() + "\n用户ID：" + UserAPI.getUserID()
                + "\n";
    }

    /**
     * 通过用户的ukey获取用户的详细信息
     *
     * @param ukey 用户ukey
     * @return ResponseObject
     */
    public static NetworkTask<UserInfo> getUserInfoByUkey(String ukey,
            RequestCallBack<UserInfo> callBack) {
        String url = "http://apis.guokr.com/community/user/" + ukey + ".json";
        return new RequestBuilder<UserInfo>()
                .get()
                .url(url)
                .useCacheFirst(true, 1200000)
                .parser(new UserInfoParser())
                .callback(callBack)
                .requestAsync();
    }

    /**
     * 通过用户id获取用户信息
     *
     * @param id 用户id
     * @return ResponseObject
     */
    public static NetworkTask<UserInfo> getUserInfoByID(String id,
            RequestCallBack<UserInfo> callBack) {
        return getUserInfoByUkey(base36Encode(Long.valueOf(id)), callBack);
    }

    /**
     * 推荐一个链接
     *
     * @param link    链接地址
     * @param title   链接标题
     * @param summary 内容概述
     * @param comment 评语
     * @return ResponseObject
     */
    public static NetworkTask<Boolean> recommendLink(String link, String title, String summary,
            String comment, RequestCallBack<Boolean> callBack) {
        String url = "http://www.guokr.com/apis/community/user/recommend.json";
        if (TextUtils.isEmpty(summary)) {
            summary = title;
        }
        ParamsMap pairs = new ParamsMap();
        pairs.put("title", title);
        pairs.put("url", link);
        pairs.put("summary", summary);
        pairs.put("comment", comment);
        pairs.put("target", "activity");
        return new RequestBuilder<Boolean>()
                .post()
                .url(url)
                .params(pairs)
                .parser(new BooleanParser())
                .callback(callBack)
                .requestAsync();
    }

    /**
     * 退出登录、清除过期数据
     */
    @SuppressWarnings("deprecation")
    public static void logout() {
        PrefsUtil.remove(Keys.Key_Cookie);
        PrefsUtil.remove(Keys.Key_Access_Token);
        PrefsUtil.remove(Keys.Key_Access_Token_2);
        PrefsUtil.remove(Keys.Key_Ukey);
        PrefsUtil.remove(Keys.Key_User_Avatar);
        PrefsUtil.remove(Keys.Key_User_ID);
        PrefsUtil.remove(Keys.Key_User_Name);
        CookieSyncManager.createInstance(App.getApp());
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.removeAllCookie();
        cookieManager.hasCookies();
        cookieManager.removeSessionCookie();
        CookieSyncManager.getInstance().sync();
        HttpUtil.clearCookies();
        BasketHelper.clearAllMyBaskets();
        PostPagerFragment.shouldNotifyDataSetChanged = true;
        Emitter.emit(new LoginStateChangedEvent());
    }

    /**
     * 获取保存的用户token
     *
     * @return 用户token，正确的话，64位长度
     */
    public static String getToken() {
        return PrefsUtil.readString(Keys.Key_Access_Token, "");
    }

    /**
     * 保存用户token
     */
    public static void setToken(String token) {
        PrefsUtil.saveString(Keys.Key_Access_Token, token);
    }


    /**
     * 获取保存的用户token
     *
     * @return 用户token，正确的话，64位长度
     */
    public static String getToken2() {
        return PrefsUtil.readString(Keys.Key_Access_Token_2, "");
    }

    /**
     * 保存用户token
     */
    public static void setToken2(String token) {
        PrefsUtil.saveString(Keys.Key_Access_Token_2, token);
    }

    /**
     * 获取保存的用户ukey
     *
     * @return 用户ukey，6位长度
     */
    public static String getUkey() {
        return PrefsUtil.readString(Keys.Key_Ukey, "");
    }

    /**
     * 获取保存的用户ukey
     *
     * @return 用户ukey，6位长度
     */
    public static void setUkey(String ukey) {
        PrefsUtil.saveString(Keys.Key_Ukey, ukey);
    }

    /**
     * 获取保存的用户id
     *
     * @return 用户id，一串数字
     */
    public static String getUserID() {
        return PrefsUtil.readString(Keys.Key_User_ID, "");
    }

    /**
     * 获取保存的用户名
     *
     * @return 用户id，一串数字
     */
    public static String getName() {
        return PrefsUtil.readString(Keys.Key_User_Name, "");
    }

    /**
     * 获取保存的用户头像地址
     *
     * @return 头像地址为http链接
     */
    public static String getUserAvatar() {
        return PrefsUtil.readString(Keys.Key_User_Avatar, "");
    }

    /**
     * 获取保存的用户cookie
     *
     * @return 用户登录时保存下来的cookie，未使用
     */
    public static String getCookie() {
        return PrefsUtil.readString(Keys.Key_Cookie, "");
    }

    /**
     * 获取保存的用户cookie
     *
     * @return 生成一个简单的cookie
     */
    public static String getSimpleCookie() {
        return "_32353_access_token=" + getToken() + "; _32353_ukey=" + getUkey() + ";";
    }
}
