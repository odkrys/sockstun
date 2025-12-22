/*
 ============================================================================
 Name        : MainActivity.java
 Author      : hev <r@hev.cc>
 Copyright   : Copyright (c) 2023 xyz
 Description : Main Activity
 ============================================================================
 */

package hev.sockstun;
import android.app.Activity;
import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends Activity implements View.OnClickListener {
	private Preferences prefs;
	private EditText edittext_socks_addr;
	private EditText edittext_socks_udp_addr;
	private EditText edittext_socks_port;
	private EditText edittext_socks_user;
	private EditText edittext_socks_pass;
	private EditText edittext_dns_ipv4;
	private EditText edittext_dns_ipv6;
	private CheckBox checkbox_udp_in_tcp;
	private CheckBox checkbox_remote_dns;
	private CheckBox checkbox_auto_start;
	private CheckBox checkbox_global;
	private CheckBox checkbox_ipv4;
	private CheckBox checkbox_ipv6;
	private Button button_apps;
	private Button button_save;
	private Button button_control;

	private BroadcastReceiver vpnStatusReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			if (TProxyService.ACTION_VPN_STATUS_CHANGED.equals(intent.getAction())) {
				checkAndSyncVpnState();
			}
		}
	};

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		prefs = new Preferences(this);
		setContentView(R.layout.main);

		edittext_socks_addr = (EditText) findViewById(R.id.socks_addr);
		edittext_socks_udp_addr = (EditText) findViewById(R.id.socks_udp_addr);
		edittext_socks_port = (EditText) findViewById(R.id.socks_port);
		edittext_socks_user = (EditText) findViewById(R.id.socks_user);
		edittext_socks_pass = (EditText) findViewById(R.id.socks_pass);
		edittext_dns_ipv4 = (EditText) findViewById(R.id.dns_ipv4);
		edittext_dns_ipv6 = (EditText) findViewById(R.id.dns_ipv6);
		checkbox_ipv4 = (CheckBox) findViewById(R.id.ipv4);
		checkbox_ipv6 = (CheckBox) findViewById(R.id.ipv6);
		checkbox_global = (CheckBox) findViewById(R.id.global);
		checkbox_udp_in_tcp = (CheckBox) findViewById(R.id.udp_in_tcp);
		checkbox_remote_dns = (CheckBox) findViewById(R.id.remote_dns);
		checkbox_auto_start = (CheckBox) findViewById(R.id.auto_start);
		button_apps = (Button) findViewById(R.id.apps);
		button_save = (Button) findViewById(R.id.save);
		button_control = (Button) findViewById(R.id.control);

		checkbox_udp_in_tcp.setOnClickListener(this);
		checkbox_remote_dns.setOnClickListener(this);
		checkbox_auto_start.setOnClickListener(this);
		checkbox_global.setOnClickListener(this);
		button_apps.setOnClickListener(this);
		button_save.setOnClickListener(this);
		button_control.setOnClickListener(this);

		/* Request VPN permission */
		Intent intent = VpnService.prepare(MainActivity.this);
		if (intent != null) {
			startActivityForResult(intent, 0);
		} else {
			if (prefs.getAutoStart()) {
				startService(new Intent(this, TProxyService.class).setAction(TProxyService.ACTION_CONNECT));
			}
		}
		requestNotificationPermission();
	}

	@Override
	protected void onResume() {
		super.onResume();
		IntentFilter filter = new IntentFilter(TProxyService.ACTION_VPN_STATUS_CHANGED);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
			registerReceiver(vpnStatusReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
		} else {
			registerReceiver(vpnStatusReceiver, filter);
		}

		updateUI();
		checkAndSyncVpnState();

		if (prefs.getAutoStart() && !isServiceRunning(TProxyService.class)) {
			Intent prepareVpnIntent = VpnService.prepare(MainActivity.this);
			if (prepareVpnIntent != null) {
				startActivityForResult(prepareVpnIntent, 0);
			} else {
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
					startForegroundService(new Intent(this, TProxyService.class).setAction(TProxyService.ACTION_CONNECT));
				} else {
					startService(new Intent(this, TProxyService.class).setAction(TProxyService.ACTION_CONNECT));
				}
			}
		}
	}

	@Override
	protected void onPause() {
		super.onPause();
		unregisterReceiver(vpnStatusReceiver);
	}

	@Override
	protected void onActivityResult(int request, int result, Intent data) {
		if (request == 0) {
			if (result == RESULT_OK) {
				if (prefs.getAutoStart()) {
					startService(new Intent(this, TProxyService.class).setAction(TProxyService.ACTION_CONNECT));
				}
				else if (prefs.getEnable()) {
					startService(new Intent(this, TProxyService.class).setAction(TProxyService.ACTION_CONNECT));
				}
			} else {
				prefs.setEnable(false);
				prefs.setAutoStart(false);
				savePrefs();
				updateUI();
				Toast.makeText(this, "VPN permission denied", Toast.LENGTH_SHORT).show();
			}
		}
	}

	@Override
	public void onClick(View view) {
		if (view == checkbox_global || view == checkbox_remote_dns) {
			savePrefs();
			updateUI();
		} else if (view == button_apps) {
			startActivity(new Intent(this, AppListActivity.class));
		} else if (view == button_save) {
			savePrefs();
			Context context = getApplicationContext();
			Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show();
		} else if (view == checkbox_auto_start) {
			prefs.setAutoStart(checkbox_auto_start.isChecked());
			savePrefs();
			updateUI();
		} else if (view == button_control) {
			boolean isEnable = prefs.getEnable();
			prefs.setEnable(!isEnable);
			savePrefs();
			updateUI();
			Intent intent = new Intent(this, TProxyService.class);
			if (isEnable)
			  startService(intent.setAction(TProxyService.ACTION_DISCONNECT));
			else
			  startService(intent.setAction(TProxyService.ACTION_CONNECT));
		}
	}

	private void checkAndSyncVpnState() {
		boolean isVpnActuallyRunningByOurService = isServiceRunning(TProxyService.class);
		boolean isVpnPermissionGranted = (VpnService.prepare(this) == null);

		boolean targetVpnState = isVpnActuallyRunningByOurService && isVpnPermissionGranted;

		if (prefs.getEnable() != targetVpnState) {
			prefs.setEnable(targetVpnState);
		}
		updateUI();
	}

	private boolean isServiceRunning(Class<?> serviceClass) {
		ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
		if (manager != null) {
			for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
				if (serviceClass.getName().equals(service.service.getClassName())) {
					return true;
				}
			}
		}
		return false;
	}

	private void requestNotificationPermission() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
			if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
					!= PackageManager.PERMISSION_GRANTED) {
				ActivityCompat.requestPermissions(this,
						new String[]{android.Manifest.permission.POST_NOTIFICATIONS},
						1001);
			}
		}
	}

	private void updateUI() {
		edittext_socks_addr.setText(prefs.getSocksAddress());
		edittext_socks_udp_addr.setText(prefs.getSocksUdpAddress());
		edittext_socks_port.setText(Integer.toString(prefs.getSocksPort()));
		edittext_socks_user.setText(prefs.getSocksUsername());
		edittext_socks_pass.setText(prefs.getSocksPassword());
		edittext_dns_ipv4.setText(prefs.getDnsIpv4());
		edittext_dns_ipv6.setText(prefs.getDnsIpv6());
		checkbox_ipv4.setChecked(prefs.getIpv4());
		checkbox_ipv6.setChecked(prefs.getIpv6());
		checkbox_global.setChecked(prefs.getGlobal());
		checkbox_udp_in_tcp.setChecked(prefs.getUdpInTcp());
		checkbox_remote_dns.setChecked(prefs.getRemoteDns());
		checkbox_auto_start.setChecked(prefs.getAutoStart());

		boolean isVpnEnabled = prefs.getEnable();
		boolean editable = !isVpnEnabled;

		edittext_socks_addr.setEnabled(editable);
		edittext_socks_udp_addr.setEnabled(editable);
		edittext_socks_port.setEnabled(editable);
		edittext_socks_user.setEnabled(editable);
		edittext_socks_pass.setEnabled(editable);
		edittext_dns_ipv4.setEnabled(editable && !prefs.getRemoteDns());
		edittext_dns_ipv6.setEnabled(editable && !prefs.getRemoteDns());
		checkbox_udp_in_tcp.setEnabled(editable);
		checkbox_remote_dns.setEnabled(editable);
		checkbox_auto_start.setEnabled(editable);
		checkbox_global.setEnabled(editable);
		checkbox_ipv4.setEnabled(editable);
		checkbox_ipv6.setEnabled(editable);
		button_apps.setEnabled(editable && !prefs.getGlobal());
		button_save.setEnabled(editable);

		if (editable)
		  button_control.setText(R.string.control_enable);
		else
		  button_control.setText(R.string.control_disable);
	}

	private void savePrefs() {
		prefs.setSocksAddress(edittext_socks_addr.getText().toString());
		prefs.setSocksUdpAddress(edittext_socks_udp_addr.getText().toString());
		prefs.setSocksPort(Integer.parseInt(edittext_socks_port.getText().toString()));
		prefs.setSocksUsername(edittext_socks_user.getText().toString());
		prefs.setSocksPassword(edittext_socks_pass.getText().toString());
		prefs.setDnsIpv4(edittext_dns_ipv4.getText().toString());
		prefs.setDnsIpv6(edittext_dns_ipv6.getText().toString());
		if (!checkbox_ipv4.isChecked() && !checkbox_ipv6.isChecked())
		  checkbox_ipv4.setChecked(prefs.getIpv4());
		prefs.setIpv4(checkbox_ipv4.isChecked());
		prefs.setIpv6(checkbox_ipv6.isChecked());
		prefs.setGlobal(checkbox_global.isChecked());
		prefs.setUdpInTcp(checkbox_udp_in_tcp.isChecked());
		prefs.setRemoteDns(checkbox_remote_dns.isChecked());
		prefs.setAutoStart(checkbox_auto_start.isChecked());
	}
}