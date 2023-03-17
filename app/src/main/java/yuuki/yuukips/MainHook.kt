package yuuki.yuukips

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.callbacks.XC_LoadPackage

class MainHook : IXposedHookLoadPackage, IXposedHookZygoteInit {
    private val hook: Hook = Hook()
    override fun handleLoadPackage(i: XC_LoadPackage.LoadPackageParam) {
        hook.handleLoadPackage(i)
    }
    override fun initZygote(s: IXposedHookZygoteInit.StartupParam) {
        hook.initZygote()
    }
}