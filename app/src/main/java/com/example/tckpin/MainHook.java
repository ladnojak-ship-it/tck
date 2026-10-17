package com.example.tckpin;

import android.content.res.XModuleResources;
import android.graphics.drawable.Drawable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ImageSpan;
import android.view.View;
import android.widget.TextView;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Хук SystemUI на Pixel 6 (Android 12-15).
 * Меняет подписи на кнопках PIN-клавиатуры экрана блокировки:
 * 1-6 -> пусто, 7 -> Т, 8 -> Ц, 9 -> К, 0 -> иконка телефона (phone_icon.png).
 * Сами кнопки продолжают вводить свои обычные цифры.
 */
public class MainHook implements IXposedHookLoadPackage, IXposedHookZygoteInit {

    private static final String NUMPAD_CLASS = "com.android.keyguard.NumPadKey";

    private static String modulePath;

    @Override
    public void initZygote(StartupParam startupParam) {
        modulePath = startupParam.modulePath;
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!"com.android.systemui".equals(lpparam.packageName)) return;

        try {
            Class<?> numPadKey = XposedHelpers.findClass(NUMPAD_CLASS, lpparam.classLoader);

            // updateText() вызывается при создании кнопки и при смене цифры
            XposedBridge.hookAllMethods(numPadKey, "updateText", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    applyLabel(param.thisObject);
                }
            });

            // на случай, если updateText в этой сборке называется иначе
            XposedBridge.hookAllConstructors(numPadKey, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    applyLabel(param.thisObject);
                }
            });
        } catch (Throwable t) {
            XposedBridge.log("TckPin: не удалось зацепить NumPadKey: " + t);
        }
    }

    private void applyLabel(Object key) {
        try {
            int digit = XposedHelpers.getIntField(key, "mDigit");
            TextView digitText = (TextView) XposedHelpers.getObjectField(key, "mDigitText");
            if (digitText == null) return;

            switch (digit) {
                case 7: digitText.setText("Т"); break;
                case 8: digitText.setText("Ц"); break;
                case 9: digitText.setText("К"); break;
                case 0: setPhoneIcon(digitText); break;
                default: digitText.setText(""); break;
            }

            // убираем маленькие буквы под цифрами (ABC, DEF...)
            Object sub = XposedHelpers.getObjectField(key, "mKlondikeText");
            if (sub instanceof View) {
                ((View) sub).setVisibility(View.GONE);
            }
        } catch (Throwable t) {
            XposedBridge.log("TckPin: applyLabel: " + t);
        }
    }

    private void setPhoneIcon(TextView target) {
        try {
            XModuleResources res = XModuleResources.createInstance(modulePath, null);
            Drawable icon = res.getDrawable(R.drawable.phone_icon, null);

            int size = Math.round(target.getTextSize() * 1.3f);
            icon.setBounds(0, 0, size, size);

            SpannableString s = new SpannableString(" ");
            s.setSpan(new ImageSpan(icon, ImageSpan.ALIGN_CENTER), 0, 1,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            target.setText(s);
        } catch (Throwable t) {
            XposedBridge.log("TckPin: setPhoneIcon: " + t);
            target.setText("📞"); // запасной вариант
        }
    }
}
