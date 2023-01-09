package yuuki.yuukips

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences
import android.content.res.XModuleResources
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.RoundRectShape
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.Base64
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.webkit.SslErrorHandler
import android.widget.*
import com.github.kyuubiran.ezxhelper.init.EzXHelperInit
import com.github.kyuubiran.ezxhelper.utils.*
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import org.json.JSONObject
import yuuki.yuukips.Utils.dp2px
import yuuki.yuukips.Utils.isInit
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.regex.Pattern
import javax.net.ssl.*
import kotlin.system.exitProcess
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff

class Hook {

    // just for login
    private val package_apk = "com.miHoYo.YuukiPS"
    private val path = "/sdcard/Android/data/${package_apk}"
    private val file_json = "${path}/files/server.json"
    private val proxyListRegex = arrayListOf( 
        // CN
        "dispatchcnglobal.yuanshen.com",
        "gameapi-account.mihoyo.com",
        "hk4e-sdk-s.mihoyo.com",
        "log-upload.mihoyo.com",
        "minor-api.mihoyo.com",
        "public-data-api.mihoyo.com",
        "sdk-static.mihoyo.com",
        "webstatic.mihoyo.com",
        "user.mihoyo.com",
        // Global
        "dispatchosglobal.yuanshen.com",        
        "api-account-os.hoyoverse.com",
        "hk4e-sdk-os-s.hoyoverse.com",
        "hk4e-sdk-os-static.hoyoverse.com",
        "hk4e-sdk-os.hoyoverse.com",
        "log-upload-os.hoyoverse.com",
        "minor-api-os.hoyoverse.com",
        "sdk-os-static.hoyoverse.com",
        "sg-public-data-api.hoyoverse.com",
        "webstatic.hoyoverse.com",
        // List Server
        "osasiadispatch.yuanshen.com",
        "oseurodispatch.yuanshen.com",
        "osusadispatch.yuanshen.com"
    )
    
    private lateinit var server: String
    private lateinit var showServer: String
    private lateinit var textJson: String

    private lateinit var modulePath: String
    private lateinit var moduleRes: XModuleResources
    private lateinit var windowManager: WindowManager

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
        return SSLContext.getInstance("TLS").apply {
            init(arrayOf<KeyManager>(), arrayOf<TrustManager>(DefaultTrustManager()), SecureRandom())
        }.socketFactory
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
        override fun checkClientTrusted(chain: Array<X509Certificate?>?, authType: String?) {
        }

        @SuppressLint("TrustAllX509TrustManager")
        override fun checkServerTrusted(chain: Array<X509Certificate?>?, authType: String?) {
        }

        override fun getAcceptedIssuers(): Array<X509Certificate> {
            return arrayOf()
        }
    }

    fun initZygote(startupParam: IXposedHookZygoteInit.StartupParam) {
        modulePath = startupParam.modulePath
        moduleRes = XModuleResources.createInstance(modulePath, null)
        TrustMeAlready().initZygote()

        // default
        server = ""
    }

    private var startForceUrl = false
    private var startProxyList = false
    private lateinit var dialog: LinearLayout

    @SuppressLint("WrongConstant", "ClickableViewAccessibility")
    fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {

        XposedBridge.log("Hi Yuuki")
        XposedBridge.log("Load: "+lpparam.packageName)

        if (lpparam.packageName == "${package_apk}") {

            XposedBridge.log("found it")
            EzXHelperInit.initHandleLoadPackage(lpparam) // idk what this?

            // json for get server
            val z3ro = File(file_json)
            if (z3ro.exists()) {
                val z3roJson = JSONObject(z3ro.readText())
                server = z3roJson.getString("server")
                XposedBridge.log("server: "+server)
            } else {
                server = "https://genshin.ps.yuuki.me"
                z3ro.writeText(TextJSON(server))
                
                XposedBridge.log("server.json not found, created")
            }

            tryhook()       
        }

        findMethod(Activity::class.java, true) { name == "onCreate" }.hookBefore { param ->
            activity = param.thisObject as Activity
            XposedBridge.log("activity: "+activity.applicationInfo.name)            
        }

        findMethod("com.miHoYo.GetMobileInfo.MainActivity") { name == "onCreate" }.hookBefore { param ->
            activity = param.thisObject as Activity
            XposedBridge.log("MainActivity")
            //enter()
            showDialog()
        }
    }

    private fun showDialog() {
        // remove folders if exist
        val z3ro = File(file_json)
        val z3roJson = JSONObject(z3ro.readText())
        if (z3roJson.getString("remove_il2cpp_folders") != "false") {
            val foldersPath = "${path}/files/il2cpp"
            val folders = File(foldersPath)
            if (folders.exists()) {
                folders.deleteRecursively()
            }
        }
        if (z3roJson.getString("move_folders") != "false") {
            fix()
        }
        AlertDialog.Builder(activity).apply {
            setCancelable(false)
            setTitle("Welcome to Private Server")
            setMessage("Click continue to play\nClick Change Server to change server location (restart required)\nInfo: discord.yuuki.me")

            setNegativeButton("Continue") { _, _ ->

                /*
                findMethodOrNull("android.app.AlertDialog\$Builder") { name == "create" }?.hookBefore {
                    XposedBridge.log("bye")                    
                    it.result = null
                }
                */
                enter()

            }
            setNeutralButton("Change Server") { _, _ ->
                RenameJSON()             
            }

        }.show()
    }

    fun TextJSON(melon:String):String{
        return "{\n\t\"server\": \""+melon+"\",\n\t\"remove_il2cpp_folders\": true,\n\t\"move_folders\": false,\n\t\"showText\": true,\n\t\"Note\": \"Always use https:// or http://, you can add port using : after server... EXAMPLE: https://genshin.ps.yuuki.me:443\"\n}"
    }

    // Move all folders name "Files" to "Backup", and move it back to "Files"
    private fun fix() {
        val backup = File("${path}/files/Backup")
        val files = File("${path}/files/Files")
        if (backup.exists()) {
            backup.deleteRecursively()
        }
        if (files.exists()) {
            files.renameTo(backup)
        }
        backup.renameTo(files)
    }

    private fun RenameJSON(){
        AlertDialog.Builder(activity).apply {
            setCancelable(false)
            setTitle("Change Servers")
            setMessage("Enter Full URL (http://2.0.0.100) or (Just blank/official for official servers) or (yuuki for server yuukips)")
            setView(ScrollView(context).apply {
                addView(EditText(activity).apply {
                    val str = ""
                    setText(str.toCharArray(), 0, str.length)
                    addTextChangedListener(object : TextWatcher {
                        override fun beforeTextChanged(p0: CharSequence, p1: Int, p2: Int, p3: Int) {}
                        override fun onTextChanged(p0: CharSequence, p1: Int, p2: Int, p3: Int) {}
                        @SuppressLint("CommitPrefEdits")
                        override fun afterTextChanged(p0: Editable) {
                            server = p0.toString()
                            if(server == "official" || server == "blank"){
                                server = "os"
                            }else if(server == "yuuki" || server == "yuukips" || server == "melon" && server != ""){
                                server = "https://genshin.ps.yuuki.me"
                            } else if (server == "https://" || server == "http://" && server != "") {
                                server = ""
                            } else if (!server.startsWith("https://") && (!server.startsWith("http://")) && server != "" && server != "official" && server != "blank" && server != "yuuki" && server != "yuukips" && server != "melon") {
                                server = "https://"+server
                            } else if (server == "") {
                                server = ""
                            }
                        }
                    })
                })
            })
            
            setPositiveButton("Save") { _, _ ->
                if (server == "" ) {
                    Toast.makeText(activity, "Domain/Server not entered, Cancel", Toast.LENGTH_LONG).show()
                    showDialog()
                } else {
                    val z3ro = File(file_json)
                    if (server == "os") {
                        server = ""
                    }
                    z3ro.writeText(TextJSON(server))
                    Toast.makeText(activity, "Changes have been saved, please restart app...", Toast.LENGTH_LONG).show()
                    Runtime.getRuntime().exit(0);
                }
            }

            setNeutralButton("Back") { _, _ ->
                showDialog()
            }

        }.show()
    }

    private fun tryhook(){
        hook()
        sslHook()
        val z3ro = File(file_json)
        val z3roJson = JSONObject(z3ro.readText())
        if (z3roJson.getString("showText") != "false") {
            showText()
        } else {
            XposedBridge.log("showText: false")
        }
    }

    private fun showText() {
        findMethodOrNull("com.miHoYo.GetMobileInfo.MainActivity") { name == "onCreate" }?.hookBefore {
            findMethodOrNull("android.view.View") { name == "onDraw" }?.hookBefore {
                val canvas = it.args[0] as Canvas
                val paint = Paint()
                val paint2 = Paint()
                paint.textAlign = Paint.Align.CENTER
                paint.color = Color.WHITE
                paint.textSize = 50f
                canvas.drawText("YuukiPS", canvas.width / 2f, canvas.height / 2f, paint)
                paint2.textAlign = Paint.Align.CENTER
                if (server == "") {
                    paint2.color = Color.RED
                    showServer = "You connecting to Official Server"
                } else {
                    paint2.color = Color.GREEN
                    showServer = "Server: $server"
                }
                paint2.textSize = 40f
                canvas.drawText(showServer, canvas.width / 2f, canvas.height / 2f + 100, paint2)
                
            }
        }
        // Broken UHHHHHHHH
        findMethodOrNull("com.mihoyoos.sdk.platform.SdkActivity") { name == "onCreate" }?.hookBefore {
            findMethodOrNull("android.view.View") { name == "onDraw" }?.hookBefore {
                val canvas = it.args[0] as Canvas
                canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
            }
        }
        
    }

    private fun enter(){
        Toast.makeText(activity, "Welcome to YuukiPS", Toast.LENGTH_LONG).show()
        Toast.makeText(activity, "Don't forget to join our discord.yuuki.me", Toast.LENGTH_LONG).show()
        Toast.makeText(activity, "Thanks chengecu and Z3RO", Toast.LENGTH_LONG).show()
    }

    // Bypass HTTPS
    private fun sslHook() {
        // OkHttp3 Hook
        findMethodOrNull("com.combosdk.lib.third.okhttp3.OkHttpClient\$Builder") { name == "build" }?.hookBefore {
            it.thisObject.invokeMethod("sslSocketFactory", args(getDefaultSSLSocketFactory()), argTypes(SSLSocketFactory::class.java))
            it.thisObject.invokeMethod("hostnameVerifier", args(getDefaultHostnameVerifier()), argTypes(HostnameVerifier::class.java))
        }
        findMethodOrNull("okhttp3.OkHttpClient\$Builder") { name == "build" }?.hookBefore {
            it.thisObject.invokeMethod("sslSocketFactory", args(getDefaultSSLSocketFactory(), DefaultTrustManager()), argTypes(SSLSocketFactory::class.java, X509TrustManager::class.java))
            it.thisObject.invokeMethod("hostnameVerifier", args(getDefaultHostnameVerifier()), argTypes(HostnameVerifier::class.java))
        }
        // WebView Hook
        arrayListOf(
            "android.webkit.WebViewClient",
            "cn.sharesdk.framework.g",
            "com.facebook.internal.WebDialog\$DialogWebViewClient",
            "com.geetest.sdk.dialog.views.GtWebView\$c",
            "com.miHoYo.sdk.webview.common.view.ContentWebView\$6"
        ).forEach {
            findMethodOrNull(it) { name == "onReceivedSslError" && parameterTypes[1] == SslErrorHandler::class.java }?.hookBefore { param ->
                (param.args[1] as SslErrorHandler).proceed()
            }
        }
        // Android HttpsURLConnection Hook
        findMethodOrNull("javax.net.ssl.HttpsURLConnection") { name == "getDefaultSSLSocketFactory" }?.hookBefore {
            it.result = getDefaultSSLSocketFactory()
        }
        findMethodOrNull("javax.net.ssl.HttpsURLConnection") { name == "setSSLSocketFactory" }?.hookBefore {
            it.result = null
        }
        findMethodOrNull("javax.net.ssl.HttpsURLConnection") { name == "setDefaultSSLSocketFactory" }?.hookBefore {
            it.result = null
        }
        findMethodOrNull("javax.net.ssl.HttpsURLConnection") { name == "setHostnameVerifier" }?.hookBefore {
            it.result = null
        }
        findMethodOrNull("javax.net.ssl.HttpsURLConnection") { name == "setDefaultHostnameVerifier" }?.hookBefore {
            it.result = null
        }
        findMethodOrNull("javax.net.ssl.HttpsURLConnection") { name == "getDefaultHostnameVerifier" }?.hookBefore {
            it.result = getDefaultHostnameVerifier()
        }
    }

    // Bypass HTTP
    private fun hook() {
        findMethod("com.miHoYo.sdk.webview.MiHoYoWebview") { name == "load" && parameterTypes[0] == String::class.java && parameterTypes[1] == String::class.java }.hookBefore {
            replaceUrl(it, 1)
        }
        findAllMethods("android.webkit.WebView") { name == "loadUrl" }.hookBefore {
            replaceUrl(it, 0)
        }
        findAllMethods("android.webkit.WebView") { name == "postUrl" }.hookBefore {
            replaceUrl(it, 0)
        }

        findMethod("okhttp3.HttpUrl") { name == "parse" && parameterTypes[0] == String::class.java }.hookBefore {
            replaceUrl(it, 0)
        }
        findMethod("com.combosdk.lib.third.okhttp3.HttpUrl") { name == "parse" && parameterTypes[0] == String::class.java }.hookBefore {
            replaceUrl(it, 0)
        }

        findMethod("com.google.gson.Gson") { name == "fromJson" && parameterTypes[0] == String::class.java && parameterTypes[1] == java.lang.reflect.Type::class.java }.hookBefore {
            replaceUrl(it, 0)
        }
        findConstructor("java.net.URL") { parameterTypes[0] == String::class.java }.hookBefore {
            replaceUrl(it, 0)
        }
        findMethod("com.combosdk.lib.third.okhttp3.Request\$Builder") { name == "url" && parameterTypes[0] == String::class.java }.hookBefore {
            replaceUrl(it, 0)
        }
        findMethod("okhttp3.Request\$Builder") { name == "url" && parameterTypes[0] == String::class.java }.hookBefore {
            replaceUrl(it, 0)
        }
    }

    // Rename
    private fun replaceUrl(method: XC_MethodHook.MethodHookParam, args: Int) {
        
        if (server == "") return
        if (method.args[args].toString() == "") return

        //XposedBridge.log("old: " + method.args[args].toString())

        for (list in proxyListRegex) {
            for (head in arrayListOf("http://", "https://")) {
                method.args[args] = method.args[args].toString().replace(head + list, server)
            }
        }

        //XposedBridge.log("new: " + method.args[args].toString())
    }
}