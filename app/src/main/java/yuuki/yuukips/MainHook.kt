package yuuki.yuukips

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.callbacks.XC_LoadPackage

class MainHook : IXposedHookLoadPackage, IXposedHookZygoteInit {
    private val hook: Hook = Hook()
    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        hook.handleLoadPackage(lpparam)
    }
    override fun initZygote(startupParam: IXposedHookZygoteInit.StartupParam) {
        hook.initZygote(startupParam)
    }
}