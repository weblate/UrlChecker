package com.trianguloy.urlchecker.modules;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;

import com.trianguloy.urlchecker.R;

/**
 * This module shows the current url and allows manual editing
 */
public class TextInputModule extends BaseModule implements TextWatcher {

    private EditText edtxt_url;
    private boolean editByCode = false;

    @Override
    public String getName() {
        return null; // not used
    }

    @Override
    public int getLayoutBase() {
        return R.layout.module_text;
    }

    @Override
    public void initialize(View views) {
        edtxt_url = views.findViewById(R.id.url);
        edtxt_url.addTextChangedListener(this);
    }

    @Override
    public void onNewUrl(String url) {
        // setText fires the afterTextChanged listener, so we need to manually disable it
        editByCode = true;
        edtxt_url.setText(url);
        editByCode = false;
    }

    // ------------------- TextWatcher -------------------

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
    }

    @Override
    public void afterTextChanged(Editable s) {
        if(editByCode) return;
        // new url by the user
        setUrl(s.toString());
    }
}