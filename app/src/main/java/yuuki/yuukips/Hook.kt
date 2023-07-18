package yuuki.yuukips

import android.annotation.SuppressLint
import android.app.Activity
import android.webkit.SslErrorHandler
import android.widget.*
import com.github.kyuubiran.ezxhelper.init.EzXHelperInit
import com.github.kyuubiran.ezxhelper.utils.*
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.io.IOException
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.regex.Pattern
import javax.net.ssl.*

class Hook {

    // App
    private val package_apk_os = "com.HoYoverse.hkrpgoversea"
    private val package_apk_real_os = "com.HoYoverse.hkrpgoversea"

    // URL Server
    private var server = "https://ps.yuuki.me"

    //  List Domain v1
    private val domain = Pattern.compile("http(s|)://.*?\\.(hoyoverse|mihoyo|yuanshen|mob|starrails)\\.com")

    //  List Domain v2
    private val more_domain =
            arrayListOf(
                    // More Domain & log
                    "globaldp-prod-os01.starrails.com",
                    "prod-official-eur-dp01.starrails.com",
                    "prod-official-usa-dp01.starrails.com",
                    "prod-official-asia-dp01.starrails.com",
                    "minor-api-os.hoyoverse.com",
                    "sdk-os-static.hoyoverse.com",
                    "hkrpg-sdk-os-static.hoyoverse.com",
                    "sdk-os-static.hoyoverse.com",
                    "webstatic.hoyoverse.com",
                    "sdk-os-static.hoyoverse.com",
                    "sg-hkrpg-api.hoyoverse.com",
                    "log-upload-os.hoyoverse.com",
                    "sg-public-data-api.hoyoverse.com",
                    "sdk-common-static.hoyoverse.com",
                    "overseauspider.yuanshen.com:8888",
            )

    // Activity
    private val activityList: ArrayList<Activity> = arrayListOf()
    private var activity: Activity
        get() {
            for (mActivity in activityList) {
                if (mActivity.isFinishing) {
                    activityList.remove(mActivity)
                } else {
                    return mActivity
                }
            }
            throw Throwable("Activity not found.")
        }
        set(value) {
            activityList.add(value)
        }

    private fun getDefaultSSLSocketFactory(): SSLSocketFactory {
        return SSLContext.getInstance("TLS")
                .apply {
                    init(
                            arrayOf<KeyManager>(),
                            arrayOf<TrustManager>(DefaultTrustManager()),
                            SecureRandom()
                    )
                }
                .socketFactory
    }

    private fun getDefaultHostnameVerifier(): HostnameVerifier {
        return DefaultHostnameVerifier()
    }

    class DefaultHostnameVerifier : HostnameVerifier {
        @SuppressLint("BadHostnameVerifier")
        override fun verify(p0: String?, p1: SSLSession?): Boolean {
            return true
        }
    }

    @SuppressLint("CustomX509TrustManager")
    private class DefaultTrustManager : X509TrustManager {

        @SuppressLint("TrustAllX509TrustManager")
        override fun checkClientTrusted(chain: Array<X509Certificate?>?, authType: String?) {}

        @SuppressLint("TrustAllX509TrustManager")
        override fun checkServerTrusted(chain: Array<X509Certificate?>?, authType: String?) {}

        override fun getAcceptedIssuers(): Array<X509Certificate> {
            return arrayOf()
        }
    }

    fun initZygote() {
        TrustMeAlready().initZygote()
    }

    @SuppressLint("WrongConstant", "ClickableViewAccessibility")
    fun handleLoadPackage(i: XC_LoadPackage.LoadPackageParam) {
        XposedBridge.log("Load: " + i.packageName)
        // Ignore other apps
        if (i.packageName == "com.HoYoverse.hkrpgoversea") {
            EzXHelperInit.initHandleLoadPackage(i)
            findMethod(Activity::class.java, true) { name == "onCreate" }.hookBefore { param ->
                activity = param.thisObject as Activity
            }
            injekhttp()
            injekssl()
        }
    }

    // Bypass HTTPS
    private fun injekssl() {
        // OkHttp3 Hook
        findMethodOrNull("com.combosdk.lib.third.okhttp3.OkHttpClient\$Builder") { name == "build" }
                ?.hookBefore {
                    it.thisObject.invokeMethod(
                            "sslSocketFactory",
                            args(getDefaultSSLSocketFactory()),
                            argTypes(SSLSocketFactory::class.java)
                    )
                    it.thisObject.invokeMethod(
                            "hostnameVerifier",
                            args(getDefaultHostnameVerifier()),
                            argTypes(HostnameVerifier::class.java)
                    )
                }
        findMethodOrNull("okhttp3.OkHttpClient\$Builder") { name == "build" }?.hookBefore {
            it.thisObject.invokeMethod(
                    "sslSocketFactory",
                    args(getDefaultSSLSocketFactory(), DefaultTrustManager()),
                    argTypes(SSLSocketFactory::class.java, X509TrustManager::class.java)
            )
            it.thisObject.invokeMethod(
                    "hostnameVerifier",
                    args(getDefaultHostnameVerifier()),
                    argTypes(HostnameVerifier::class.java)
            )
        }
        // WebView Hook
        arrayListOf(
                        "android.webkit.WebViewClient",
                        // "cn.sharesdk.framework.g",
                        // "com.facebook.internal.WebDialog\$DialogWebViewClient",
                        "com.geetest.sdk.dialog.views.GtWebView\$c",
                        "com.miHoYo.sdk.webview.common.view.ContentWebView\$6"
                )
                .forEach {
                    findMethodOrNull(it) {
                        name == "onReceivedSslError" &&
                                parameterTypes[1] == SslErrorHandler::class.java
                    }
                            ?.hookBefore { param -> (param.args[1] as SslErrorHandler).proceed() }
                }
        // Android HttpsURLConnection Hook
        findMethodOrNull("javax.net.ssl.HttpsURLConnection") {
            name == "getDefaultSSLSocketFactory"
        }
                ?.hookBefore { it.result = getDefaultSSLSocketFactory() }
        findMethodOrNull("javax.net.ssl.HttpsURLConnection") { name == "setSSLSocketFactory" }
                ?.hookBefore { it.result = null }
        findMethodOrNull("javax.net.ssl.HttpsURLConnection") {
            name == "setDefaultSSLSocketFactory"
        }
                ?.hookBefore { it.result = null }
        findMethodOrNull("javax.net.ssl.HttpsURLConnection") { name == "setHostnameVerifier" }
                ?.hookBefore { it.result = null }
        findMethodOrNull("javax.net.ssl.HttpsURLConnection") {
            name == "setDefaultHostnameVerifier"
        }
                ?.hookBefore { it.result = null }
        findMethodOrNull("javax.net.ssl.HttpsURLConnection") {
            name == "getDefaultHostnameVerifier"
        }
                ?.hookBefore { it.result = getDefaultHostnameVerifier() }
    }

    // Bypass HTTP
    private fun injekhttp() {
        findMethod("com.miHoYo.sdk.webview.MiHoYoWebview") {
            name == "load" &&
                    parameterTypes[0] == String::class.java &&
                    parameterTypes[1] == String::class.java
        }
                .hookBefore { replaceUrl(it, 1) }
        findAllMethods("android.webkit.WebView") { name == "loadUrl" }.hookBefore {
            replaceUrl(it, 0)
        }
        findAllMethods("android.webkit.WebView") { name == "postUrl" }.hookBefore {
            replaceUrl(it, 0)
        }

        findMethod("okhttp3.HttpUrl") { name == "parse" && parameterTypes[0] == String::class.java }
                .hookBefore { replaceUrl(it, 0) }
        findMethod("com.combosdk.lib.third.okhttp3.HttpUrl") {
            name == "parse" && parameterTypes[0] == String::class.java
        }
                .hookBefore { replaceUrl(it, 0) }

        findMethod("com.google.gson.Gson") {
            name == "fromJson" &&
                    parameterTypes[0] == String::class.java &&
                    parameterTypes[1] == java.lang.reflect.Type::class.java
        }
                .hookBefore { replaceUrl(it, 0) }
        findConstructor("java.net.URL") { parameterTypes[0] == String::class.java }.hookBefore {
            replaceUrl(it, 0)
        }
        findMethod("com.combosdk.lib.third.okhttp3.Request\$Builder") {
            name == "url" && parameterTypes[0] == String::class.java
        }
                .hookBefore { replaceUrl(it, 0) }
        findMethod("okhttp3.Request\$Builder") {
            name == "url" && parameterTypes[0] == String::class.java
        }
                .hookBefore { replaceUrl(it, 0) }
    }

    // Rename
    private fun replaceUrl(method: XC_MethodHook.MethodHookParam, args: Int) {

        // skip if server if empty
        if (server == "") return

        if (method.args[args] == null) return
        var melon = method.args[args].toString()
        // skip if string is empty
        if (melon == "") return

        // normal edit 1
        for (list in more_domain) {
            for (head in arrayListOf("http://", "https://")) {
                method.args[args] = method.args[args].toString().replace(head + list, server)
                // log_print("rename_v1 " + melon + " > " + method.args[args])
            }
        }

        // normal edit 2
        val m = domain.matcher(melon)
        if (m.find()) {
            method.args[args] = m.replaceAll(server)
            // log_print("rename_v2 " + melon + " > " + method.args[args])
        } else {
            // log_print("skip_rename_v2 " + melon + " > " + method.args[args])
        }
    }

    private fun log_print(text: String) {
        try {
            XposedBridge.log(text)
        } catch (e: IOException) {
            // skip
        }
    }
}
