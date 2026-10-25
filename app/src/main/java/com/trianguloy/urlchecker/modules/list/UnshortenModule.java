package com.trianguloy.urlchecker.modules.list;

import static com.trianguloy.urlchecker.utilities.methods.AndroidUtils.MARKER;
import static com.trianguloy.urlchecker.utilities.methods.AndroidUtils.getStringWithPlaceholder;

import android.content.Context;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.trianguloy.urlchecker.R;
import com.trianguloy.urlchecker.activities.ModulesActivity;
import com.trianguloy.urlchecker.dialogs.MainDialog;
import com.trianguloy.urlchecker.modules.AModuleConfig;
import com.trianguloy.urlchecker.modules.AModuleData;
import com.trianguloy.urlchecker.modules.AModuleDialog;
import com.trianguloy.urlchecker.modules.AutomationRules;
import com.trianguloy.urlchecker.modules.companions.UnshortenUtility;
import com.trianguloy.urlchecker.url.UrlData;
import com.trianguloy.urlchecker.utilities.generics.GenericPref;
import com.trianguloy.urlchecker.utilities.methods.AndroidUtils;

import org.json.JSONException;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

/**
 * Module to Unshort links by using https://unshorten.me/
 * Consider adding other services, or even allow custom
 * TODO: the redirect logic here and in the Status check module is very similar, consider extracting a common wrapper
 */
public class UnshortenModule extends AModuleData {

    public static final String PREF = "unshorten_token";

    static GenericPref.StringPref TOKEN_PREF(Context cntx) {
        return new GenericPref.StringPref(PREF, "", cntx);
    }

    @Override
    public String getId() {
        return "unshorten";
    }

    @Override
    public int getName() {
        return R.string.mUnshort_name;
    }

    @Override
    public AModuleDialog getDialog(MainDialog cntx) {
        return new UnshortenDialog(cntx);
    }

    @Override
    public AModuleConfig getConfig(ModulesActivity cntx) {
        return new UnshortenConfig(cntx);
    }

    @Override
    public List<AutomationRules.Automation<AModuleDialog>> getAutomations() {
        return (List<AutomationRules.Automation<AModuleDialog>>) (List<?>) UnshortenDialog.AUTOMATIONS;
    }
}

class UnshortenConfig extends AModuleConfig {

    final GenericPref.StringPref token;

    public UnshortenConfig(ModulesActivity cntx) {
        super(cntx);
        this.token = UnshortenModule.TOKEN_PREF(cntx);
    }

    @Override
    public int getLayoutId() {
        return R.layout.config_unshorten;
    }

    @Override
    public void onInitialize(View views) {
        // textview label
        views.<TextView>findViewById(R.id.label).setText(getStringWithPlaceholder(getActivity(), R.string.mUnshort_desc, R.string.unshorten_url));

        // set and configure input
        token.attachToEditText(views.findViewById(R.id.token));
    }
}

class UnshortenDialog extends AModuleDialog {

    static final List<AutomationRules.Automation<UnshortenDialog>> AUTOMATIONS = List.of(
            new AutomationRules.Automation<>("unshort", R.string.auto_unshort, dialog ->
                    dialog.unshort(dialog.getUrlData().disableUpdates))
    );

    private Button unshort;
    private TextView info;
    private final GenericPref.StringPref token;

    private Thread thread = null;

    public UnshortenDialog(MainDialog dialog) {
        super(dialog);
        token = UnshortenModule.TOKEN_PREF(dialog);
    }

    @Override
    public int getLayoutId() {
        return R.layout.button_text;
    }

    @Override
    public void onInitialize(View views) {
        unshort = views.findViewById(R.id.button);
        unshort.setText(R.string.mUnshort_unshort);
        unshort.setOnClickListener(v -> unshort(false));

        info = views.findViewById(R.id.text);
        info.setMovementMethod(LinkMovementMethod.getInstance());
    }

    @Override
    public void onPrepareUrl(UrlData urlData) {
        // cancel previous check if pending
        if (thread != null) {
            thread.interrupt();
            thread = null;
        }
    }

    @Override
    public void onDisplayUrl(UrlData urlData) {
        // reset all
        unshort.setEnabled(true);
        info.setText("");
        AndroidUtils.clearRoundedColor(info);
    }

    /** Unshorts the current url */
    private void unshort(boolean disableUpdates) {
        // disable button and run in background
        unshort.setEnabled(false);
        info.setText(R.string.mUnshort_checking);
        AndroidUtils.clearRoundedColor(info);

        thread = new Thread(() -> _check(disableUpdates));
        thread.start();
    }

    private void _check(boolean disableUpdates) {

        try {
            UnshortenUtility.UnshortenResult result = UnshortenUtility.unshort(getUrl(), token.get());

            // exit if was canceled
            if (Thread.currentThread().isInterrupted()) {
                Log.d("THREAD", "Interrupted");
                return;
            }

            if (!result.success()) {
                // server error, maybe too many checks
                getActivity().runOnUiThread(() -> {
                    info.setText(getActivity().getString(R.string.mUnshort_error, result.error()));
                    AndroidUtils.setRoundedColor(R.color.warning, info);
                    // allow retries
                    unshort.setEnabled(true);
                });
            } else if (Objects.equals(result.finalUrl(), getUrl())) {
                // same, nothing to replace
                getActivity().runOnUiThread(() -> {
                    info.setText(getActivity().getString(R.string.mUnshort_notFound));
                    if (result.remainingCalls() <= result.usageLimit() / 2)
                        info.append(" (" + getActivity().getString(R.string.mUnshort_pending, result.remainingCalls(), result.usageLimit()) + ")");
                    AndroidUtils.clearRoundedColor(info);
                });
            } else {
                // correct, replace
                getActivity().runOnUiThread(() -> {

                    if (!disableUpdates) {
                        // unshort to new url
                        unshortTo(result.finalUrl());
                    } else {
                        // show unshorted url
                        info.setText(AndroidUtils.underlineUrl(getActivity().getString(R.string.mUnshort_to, MARKER), result.finalUrl(), this::unshortTo));
                    }

                    if (result.remainingCalls() <= result.usageLimit() / 2)
                        info.append(" (" + getActivity().getString(R.string.mUnshort_pending, result.remainingCalls(), result.usageLimit()) + ")");

                    // a short url can be unshorted to another short url
                    unshort.setEnabled(true);
                });
            }

        } catch (IOException | JSONException e) {
            // internal error
            AndroidUtils.assertError("Unable to unshorten url", e);

            // exit if was canceled
            if (Thread.currentThread().isInterrupted()) {
                Log.d("THREAD", "Interrupted");
                return;
            }

            getActivity().runOnUiThread(() -> {
                info.setText(getActivity().getString(R.string.mUnshort_internal, e.getMessage()));
                AndroidUtils.setRoundedColor(R.color.bad, info);
                unshort.setEnabled(true);
            });
        }

    }

    /** Unshorts to another url */
    private void unshortTo(String url) {
        setUrl(new UrlData(url).dontTriggerOwn());
        info.setText(getActivity().getString(R.string.mUnshort_ok));
        AndroidUtils.setRoundedColor(R.color.good, info);
    }

}
