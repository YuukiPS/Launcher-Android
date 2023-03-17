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
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.Date
import java.util.regex.Pattern
import javax.net.ssl.*

class Hook {

    // App
    private val package_apk = "com.moe.yuukips"
    private val package_apk_real = "com.miHoYo.GenshinImpact"
    private val injek_activity = "com.miHoYo.GetMobileInfo.MainActivity"

    // URL Server
    private var server = "https://login.yuuki.me"

    //  List Domain v1
    private val domain = Pattern.compile("http(s|)://.*?\\.(hoyoverse|mihoyo|yuanshen|mob)\\.com")

    //  List Domain v2
    private val more_domain =
            arrayListOf(
                    // More Domain & log
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
        XposedBridge.log("Load: " + i.packageName) // debug

        // Ignore other apps
        if (i.packageName != "${package_apk}") {
            return
        }

        // Startup
        EzXHelperInit.initHandleLoadPackage(i)

        // Hook Activity
        findMethod(injek_activity) { name == "onCreate" }.hookBefore { param ->
            activity = param.thisObject as Activity

            // Enter
            Enter()

            // Injek here bed
        }

        // Injek here good
        Injek()
    }

    private fun Injek() {
        injekhttp()
        injekssl()
    }

    private fun Enter() {
        Toast.makeText(activity, "Welcome to YuukiPS", Toast.LENGTH_LONG).show()
        Toast.makeText(activity, "Join our discord.yuuki.me", Toast.LENGTH_LONG).show()
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

        var melon = method.args[args].toString()

        // skip if string is empty
        if (melon == "") return

        // skip config areal (BAD 3.5)
        // if (melon.startsWith("[{\"area\":")) return

        // skip for support download game data
        if (melon.startsWith("autopatchhk.yuanshen.com")) return
        if (melon.startsWith("autopatchcn.yuanshen.com")) return

        // rename package name
        /*
        if (melon.startsWith(package_apk_real)) {
            method.args[args] = melon.replace(package_apk_real, package_apk)
            log_print("rename_v1 " + melon + " > " + method.args[args] + " ")
        }
        */

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
            // check if file not exist then create it
            val file = File(activity.getExternalFilesDir(null), "log-yuuki.txt")
            if (!file.exists()) {
                file.createNewFile()
            }

            // write log to file
            val fileWriter = FileWriter(file, true)
            val bufferedWriter = BufferedWriter(fileWriter)
            var mel = "[" + SimpleDateFormat("HH:mm:ss").format(Date()) + "] " + text
            XposedBridge.log(mel) // debug
            bufferedWriter.write(mel)
            bufferedWriter.newLine()
            bufferedWriter.close()
        } catch (e: IOException) {
            Toast.makeText(
                            activity,
                            "There is no storage space permission, please allow it first",
                            Toast.LENGTH_LONG
                    )
                    .show()
            activity.finish()
        }
    }
}
