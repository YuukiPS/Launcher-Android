package yuuki.yuukips;

import static de.robv.android.xposed.XposedHelpers.*;

import de.robv.android.xposed.XC_MethodReplacement;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TrustMeAlready {

  private static final String SSL_CLASS_NAME =
    "com.android.org.conscrypt.TrustManagerImpl";
  private static final String SSL_METHOD_NAME = "checkTrustedRecursive";
  private static final Class<?> SSL_RETURN_TYPE = List.class;
  private static final Class<?> SSL_RETURN_PARAM_TYPE = X509Certificate.class;

  public void initZygote() {

    for (Method method : findClass(SSL_CLASS_NAME, null).getDeclaredMethods()) {
      if (!checkSSLMethod(method)) {
        continue;
      }

      List<Object> params = new ArrayList<>();
      params.addAll(Arrays.asList(method.getParameterTypes()));
      params.add(
        new XC_MethodReplacement() {
          @Override
          protected Object replaceHookedMethod(MethodHookParam param)
            throws Throwable {
            return new ArrayList<X509Certificate>();
          }
        }
      );
      findAndHookMethod(
        SSL_CLASS_NAME,
        null,
        SSL_METHOD_NAME,
        params.toArray()
      );
    }
  }

  private boolean checkSSLMethod(Method method) {
    if (!method.getName().equals(SSL_METHOD_NAME)) {
      return false;
    }

    // check return type
    if (!SSL_RETURN_TYPE.isAssignableFrom(method.getReturnType())) {
      return false;
    }

    // check if parameterized return type
    Type returnType = method.getGenericReturnType();
    if (!(returnType instanceof ParameterizedType)) {
      return false;
    }

    // check parameter type
    Type[] args = ((ParameterizedType) returnType).getActualTypeArguments();
    if (args.length != 1 || !(args[0].equals(SSL_RETURN_PARAM_TYPE))) {
      return false;
    }
    return true;
  }
}
