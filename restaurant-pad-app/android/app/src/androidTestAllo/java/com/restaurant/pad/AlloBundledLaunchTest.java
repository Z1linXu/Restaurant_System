package com.restaurant.pad;

import static org.junit.Assert.*;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.security.NetworkSecurityPolicy;
import android.view.ViewGroup;
import android.webkit.WebView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class AlloBundledLaunchTest {
    @Test public void independentSandboxHttpsAndBundledInjection() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertEquals("com.allonouilles.pos", context.getPackageName());
        assertEquals("Allô Nouilles POS", context.getApplicationInfo().loadLabel(context.getPackageManager()).toString());
        assertTrue(context.getApplicationInfo().dataDir.endsWith("/com.allonouilles.pos"));
        assertFalse(NetworkSecurityPolicy.getInstance().isCleartextTrafficPermitted("pos.allonouilles.com"));
        assertFalse(context.getSharedPreferences("restaurant_pad_settings", 0).contains("pad_direct_device_token"));
        FrontendAssetPathHandler handler = new FrontendAssetPathHandler(context,
            () -> BuildConfig.FIXED_API_BASE_URL, () -> "", () -> "1.0.0");
        String html = new String(handler.handle("/").getData().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(html.contains("Content-Security-Policy"));
        assertTrue(html.contains("wss://pos.allonouilles.com"));
        assertTrue(html.contains("brandName: 'Allô Nouilles'"));
        assertFalse(html.contains("https://pos.lanzhounoodlesmtl.com"));
        assertFalse(html.contains("https://test.lanzhounoodlesmtl.com"));
    }

    @Test public void actualWebViewColdLaunchShowsEmptyAlloLogin() throws Exception {
        android.app.Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Context context = instrumentation.getTargetContext();
        Activity activity = instrumentation.startActivitySync(new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try {
            String[] raw = {"null"};
            JSONObject dom = null;
            long deadline = System.currentTimeMillis() + 60000;
            while (System.currentTimeMillis() < deadline) {
                CountDownLatch ready = new CountDownLatch(1);
                instrumentation.runOnMainSync(() -> {
                    WebView web = (WebView)((ViewGroup)activity.findViewById(android.R.id.content)).getChildAt(0);
                    web.evaluateJavascript("JSON.stringify({text:document.body.innerText,brands:Array.from(document.images).filter(e=>e.complete&&e.naturalWidth>0).map(e=>e.alt),origin:location.origin,api:window.__RESTAURANT_API_BASE_URL__,ws:window.__RESTAURANT_WS_BASE_URL__,inputs:Array.from(document.querySelectorAll('input')).map(e=>e.value)})", value -> { raw[0] = value; ready.countDown(); });
                });
                assertTrue(ready.await(5, TimeUnit.SECONDS));
                Object decoded = new org.json.JSONTokener(raw[0]).nextValue();
                if (decoded instanceof String) {
                    dom = new JSONObject((String)decoded);
                    if (dom.optJSONArray("brands").toString().contains("Allô Nouilles") && dom.optJSONArray("inputs").length() == 2) break;
                }
                Thread.sleep(200);
            }
            assertNotNull(dom);
            assertEquals("https://restaurant-pad.local", dom.getString("origin"));
            assertEquals("https://pos.allonouilles.com", dom.getString("api"));
            assertEquals("wss://pos.allonouilles.com", dom.getString("ws"));
            assertTrue(dom.getString("text"), dom.getJSONArray("brands").toString().contains("Allô Nouilles"));
            assertEquals(2, dom.getJSONArray("inputs").length());
            assertEquals("", dom.getJSONArray("inputs").getString(0));
            assertEquals("", dom.getJSONArray("inputs").getString(1));
            File evidence = new File(context.getExternalFilesDir(null), "allo-login.png");
            try (FileOutputStream output = new FileOutputStream(evidence)) {
                instrumentation.getUiAutomation().takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, output);
            }
        } finally { instrumentation.runOnMainSync(activity::finish); }
    }
    // Optional acceptance input lives only in this test app sandbox, never in APK assets.
    // This test logs in and reads pages; it never submits, pairs, changes settings or prints.
    @Test public void bundledProductionReadOnlyLoginAndMenu() throws Exception {
        android.app.Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Context context = instrumentation.getTargetContext();
        File input = new File(context.getFilesDir(), "qa-login.json");
        org.junit.Assume.assumeTrue("Provide private runtime credentials for optional live read-only acceptance", input.isFile());
        JSONObject account;
        try (java.io.FileInputStream stream = new java.io.FileInputStream(input)) {
            account = new JSONObject(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        }
        Activity activity = instrumentation.startActivitySync(new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try {
            waitForJavascript(activity, "document.querySelectorAll('input').length===2", "empty login form");
            String values = "[" + JSONObject.quote(account.getString("username")) + "," + JSONObject.quote(account.getString("password")) + "]";
            evaluate(activity, "(function(){const v=" + values + ";document.querySelectorAll('input').forEach((e,i)=>{Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set.call(e,v[i]);e.dispatchEvent(new Event('input',{bubbles:true}));});return true;})()");
            evaluate(activity, "document.querySelector('form').requestSubmit();true");
            waitForJavascript(activity, "!document.querySelector('input[type=password]')&&location.pathname!='/login'&&document.body.innerText.includes('Allô')", "authorized workspace");
            evaluate(activity, "window.__alloQaRealtime=[];window.__alloQaInfo=console.info.bind(console);console.info=function(label,details){if(label==='[frontdesk-realtime]'&&details&&details.phase)window.__alloQaRealtime.push(details.phase);window.__alloQaInfo.apply(console,arguments);};true");
            navigate(activity, "/stores/1/frontdesk");
            waitForJavascript(activity, "document.body.innerText.includes('TEST 1')", "frontdesk tables");
            waitForJavascript(activity, "window.__alloQaRealtime.includes('CONNECTED')", "actual bundled SockJS/STOMP connection");
            navigate(activity, "/stores/1/frontdesk/menu?slot=TEST%201&table=TEST%201&type=dine_in");
            waitForJavascript(activity, "Array.from(document.querySelectorAll('[role=button]')).some(e=>e.querySelector('h3')&&e.innerText.includes('A1')&&e.innerText.includes('4.99'))", "loaded Allô menu card and price");
            Thread.sleep(500); // Let the compositor display the verified DOM before evidence capture.
            screenshot(context, "allo-menu.png");
            navigate(activity, "/stores/1/admin/settings/printing");
            waitForJavascript(activity, "Array.from(document.querySelectorAll('button')).some(e=>e.innerText.startsWith('MOCK')&&e.disabled)&&Array.from(document.querySelectorAll('button')).some(e=>e.innerText.includes('PAD_DIRECT')&&!e.disabled)", "loaded read-only Print Center in MOCK mode");
            assertEquals("true", evaluate(activity, "typeof RestaurantPrinter.printRawTcp==='function'&&typeof RestaurantPadDevice.getDeviceStatus==='function'"));
            String status = evaluate(activity, "RestaurantPadDevice.getDeviceStatus()");
            assertFalse("unpaired native device", new JSONObject((String)new org.json.JSONTokener(status).nextValue()).getBoolean("paired"));
            evaluate(activity, "Array.from(document.querySelectorAll('div')).find(e=>e.childElementCount===0&&/^(Print Center|打印中心|Centre d’impression)$/.test(e.innerText))?.scrollIntoView();true");
            Thread.sleep(500);
            screenshot(context, "allo-print-center.png");
        } finally {
            input.delete();
            evaluate(activity, "localStorage.removeItem('allo-nouilles:restaurant_pos_access_token');localStorage.removeItem('allo-nouilles:restaurant_pos_refresh_token');true");
            instrumentation.runOnMainSync(activity::finish);
        }
    }

    private static String evaluate(Activity activity, String javascript) throws Exception {
        String[] result = {"null"};
        CountDownLatch ready = new CountDownLatch(1);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            WebView web = (WebView)((ViewGroup)activity.findViewById(android.R.id.content)).getChildAt(0);
            web.evaluateJavascript(javascript, value -> {result[0] = value; ready.countDown();});
        });
        assertTrue(ready.await(5, TimeUnit.SECONDS));
        return result[0];
    }

    private static void waitForJavascript(Activity activity, String condition, String step) throws Exception {
        long deadline = System.currentTimeMillis()+60000;
        do {
            if ("true".equals(evaluate(activity, condition))) return;
            Thread.sleep(200);
        } while (System.currentTimeMillis()<deadline);
        fail("Timed out: " + step);
    }

    private static void navigate(Activity activity, String path) throws Exception {
        evaluate(activity, "history.pushState({},'',"+JSONObject.quote(path)+");window.dispatchEvent(new PopStateEvent('popstate'));true");
    }

    private static void screenshot(Context context, String name) throws Exception {
        try (FileOutputStream output = new FileOutputStream(new File(context.getExternalFilesDir(null),name))) {
            InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot().compress(Bitmap.CompressFormat.PNG,100,output);
        }
    }

}
