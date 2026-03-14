package com.routerassistant;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.webkit.CookieManager;
import android.webkit.WebStorage;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.List;

public class MainActivity extends AppCompatActivity implements RouterAdapter.OnRouterClickListener {

    private TextInputLayout tilAddress;
    private TextInputEditText etAddress;
    private RecyclerView rvRouters;
    private TextView tvEmpty;
    private RouterAdapter adapter;
    private PreferenceHelper prefHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefHelper = new PreferenceHelper(this);
        initViews();
        loadSavedRouters();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadSavedRouters();
    }

    private void initViews() {
        tilAddress = findViewById(R.id.til_address);
        etAddress = findViewById(R.id.et_address);
        MaterialButton btnConnect = findViewById(R.id.btn_connect);
        rvRouters = findViewById(R.id.rv_routers);
        tvEmpty = findViewById(R.id.tv_empty);

        adapter = new RouterAdapter();
        adapter.setListener(this);
        rvRouters.setLayoutManager(new LinearLayoutManager(this));
        rvRouters.setAdapter(adapter);

        btnConnect.setOnClickListener(v -> onConnectClicked());

        etAddress.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO) {
                onConnectClicked();
                return true;
            }
            return false;
        });

        String lastAddress = prefHelper.getLastAddress();
        if (!lastAddress.isEmpty()) {
            etAddress.setText(lastAddress);
        }
    }

    private void onConnectClicked() {
        String address = etAddress.getText() != null ? etAddress.getText().toString().trim() : "";

        if (address.isEmpty()) {
            tilAddress.setError(getString(R.string.error_empty_address));
            return;
        }

        if (!isValidAddress(address)) {
            tilAddress.setError(getString(R.string.error_invalid_address));
            return;
        }

        tilAddress.setError(null);
        navigateToWebView(address);
    }

    private boolean isValidAddress(String address) {
        String addr = address.replaceFirst("^https?://", "");
        // Basic validation: not empty, no spaces
        return !addr.isEmpty() && !addr.contains(" ");
    }

    private void navigateToWebView(String address) {
        Intent intent = new Intent(this, WebViewActivity.class);
        intent.putExtra(WebViewActivity.EXTRA_ADDRESS, address);
        startActivity(intent);
    }

    private void loadSavedRouters() {
        List<RouterInfo> routers = prefHelper.getRouters();
        adapter.setRouters(routers);
        tvEmpty.setVisibility(routers.isEmpty() ? View.VISIBLE : View.GONE);
        rvRouters.setVisibility(routers.isEmpty() ? View.GONE : View.VISIBLE);
    }

    @Override
    public void onRouterClick(RouterInfo router) {
        etAddress.setText(router.getAddress());
        navigateToWebView(router.getAddress());
    }

    @Override
    public void onRouterLongClick(RouterInfo router, int position) {
        String[] items = {
                getString(R.string.menu_set_alias),
                getString(R.string.menu_view_password)
        };
        new MaterialAlertDialogBuilder(this)
                .setTitle(router.getDisplayName())
                .setItems(items, (dialog, which) -> {
                    if (which == 0) {
                        showAliasDialog(router, position);
                    } else if (which == 1) {
                        showPasswordDialog(router);
                    }
                })
                .show();
    }

    private void showAliasDialog(RouterInfo router, int position) {
        TextInputLayout til = new TextInputLayout(this);
        til.setHint(getString(R.string.hint_alias));
        til.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        til.setBoxCornerRadii(12, 12, 12, 12);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        til.setPadding(pad, pad / 2, pad, 0);

        TextInputEditText etAlias = new TextInputEditText(til.getContext());
        etAlias.setText(router.getAlias());
        etAlias.setSelectAllOnFocus(true);
        til.addView(etAlias);

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_alias_title)
                .setView(til)
                .setPositiveButton(R.string.btn_save, (dialog, which) -> {
                    String alias = etAlias.getText() != null ? etAlias.getText().toString().trim() : "";
                    router.setAlias(alias);
                    prefHelper.saveRouter(router);
                    adapter.updateAt(position, router);
                    Toast.makeText(this,
                            alias.isEmpty() ? R.string.msg_alias_cleared : R.string.msg_alias_saved,
                            Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    private void showPasswordDialog(RouterInfo router) {
        String username = router.getUsername();
        String password = router.getPassword();

        StringBuilder info = new StringBuilder();
        info.append(getString(R.string.hint_router_address)).append("\n").append(router.getAddress());
        if (!username.isEmpty()) {
            info.append("\n\n").append(getString(R.string.label_username_full, username));
        }
        info.append("\n\n").append(getString(R.string.label_password,
                password.isEmpty() ? getString(R.string.msg_no_password) : password));

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                .setTitle(router.getDisplayName())
                .setMessage(info.toString())
                .setNegativeButton(R.string.btn_close, null);

        if (!password.isEmpty()) {
            builder.setPositiveButton(R.string.btn_copy_password, (dialog, which) -> {
                ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                if (cm != null) {
                    cm.setPrimaryClip(ClipData.newPlainText("password", password));
                    Toast.makeText(this, R.string.msg_password_copied, Toast.LENGTH_SHORT).show();
                }
            });
        }

        builder.show();
    }

    @Override
    public void onRouterDelete(RouterInfo router, int position) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_delete_title)
                .setMessage(getString(R.string.dialog_delete_message))
                .setPositiveButton(R.string.btn_delete, (dialog, which) -> {
                    prefHelper.deleteRouter(router.getAddress());
                    CookieManager.getInstance().removeAllCookies(null);
                    CookieManager.getInstance().flush();
                    WebStorage.getInstance().deleteAllData();
                    adapter.removeAt(position);
                    if (adapter.getItemCount() == 0) {
                        tvEmpty.setVisibility(View.VISIBLE);
                        rvRouters.setVisibility(View.GONE);
                    }
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }
}
