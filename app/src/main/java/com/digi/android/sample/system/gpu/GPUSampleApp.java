/*
 * Copyright (c) 2016, Digi International Inc. <support@digi.com>
 *
 * Permission to use, copy, modify, and/or distribute this software for any
 * purpose with or without fee is hereby granted, provided that the above
 * copyright notice and this permission notice appear in all copies.
 *
 * THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES
 * WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR
 * ANY SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
 * WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN
 * ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF
 * OR IN CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.
 */

package com.digi.android.sample.system.gpu;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.util.Log;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import com.androidplot.Plot;
import com.androidplot.ui.AnchorPosition;
import com.androidplot.ui.SizeLayoutType;
import com.androidplot.ui.SizeMetrics;
import com.androidplot.ui.XLayoutStyle;
import com.androidplot.ui.YLayoutStyle;
import com.androidplot.xy.BoundaryMode;
import com.androidplot.xy.LineAndPointFormatter;
import com.androidplot.xy.SimpleXYSeries;
import com.androidplot.xy.XYPlot;
import com.androidplot.xy.YValueMarker;
import com.digi.android.system.cpu.CPUManager;
import com.digi.android.system.cpu.ICPUTemperatureListener;
import com.digi.android.system.cpu.exception.CPUTemperatureException;
import com.digi.android.system.gpu.GPUManager;

import java.io.IOException;
import java.text.DecimalFormat;
import java.util.Locale;

import fi.harism.effects.ViewPentagons;

/**
 * GPU sample application.
 *
 * <p>This example lets you adjust the GPU multiplier and monitor the module's
 * temperature while an OpenGL application is running and using the GPU.</p>
 *
 * <p>For a complete description on the example, refer to the 'README.md' file
 * included in the example directory.</p>
 */
public class GPUSampleApp extends Activity {

	// Constants.
	private static final String TAG = "GPUSampleApp";

	public static final String INTENT_FPS = "fps";

	private static final int TEMPERATURE_INTERVAL = 1000;
	private static final int MAX_TIME = 60;
	private static final int MAX_MULTIPLIER = 64;

	// Variables.
	private CPUManager cpuManager;
	private GPUManager gpuManager;

	private TextView tvFps;
	private TextView tvCurrentTemperature;
	private TextView tvGpuMultiplier;
	private SeekBar sbMultiplier;

	private XYPlot tempPlot;
	private SimpleXYSeries tempSeries;

	private GLSurfaceView mGLSurfaceView;

	private boolean gpuMultError = false;

	private final ICPUTemperatureListener temperatureListener = new ICPUTemperatureListener() {
		@Override
		public void onTemperatureUpdate(float temperature) {
			// Update graph.
			if (tempSeries.size() > MAX_TIME)
				tempSeries.removeFirst();
			tempSeries.addLast(null, temperature);
			tempPlot.redraw();

			// Update temperature label.
			tvCurrentTemperature.setText(String.format(getResources().getString(R.string.current_temperature), temperature));

			// Update multiplier label and seek bar.
			if (!gpuMultError) {
				try {
					int multiplier = gpuManager.getMultiplier();
					tvGpuMultiplier.setText(String.format(getResources().getString(R.string.gpu_multiplier), multiplier));
					if (multiplier != (sbMultiplier.getProgress() + 1))
						sbMultiplier.setProgress(multiplier - 1);
				} catch (IOException e) {
					e.printStackTrace();
				} catch (UnsupportedOperationException e) {
					e.printStackTrace();
				}
			}
		}
	};

	private final BroadcastReceiver fpsReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			tvFps.setText(String.format(Locale.getDefault(), "%d FPS", intent.getIntExtra(INTENT_FPS, 0)));
		}
	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.main);

		cpuManager = new CPUManager(this);
		gpuManager = new GPUManager(this);

		// Initialize the application controls.
		initializeControls();
	}

	@Override
	protected void onResume() {
		super.onResume();
		if (mGLSurfaceView != null)
			mGLSurfaceView.onResume();
		cpuManager.registerListener(temperatureListener, TEMPERATURE_INTERVAL);
		registerReceiver(fpsReceiver, new IntentFilter(INTENT_FPS));
	}

	@Override
	protected void onPause() {
		super.onPause();
		if (mGLSurfaceView != null)
			mGLSurfaceView.onPause();
		cpuManager.unregisterListener(temperatureListener);
		unregisterReceiver(fpsReceiver);
	}

	/**
	 * Initializes application controls.
	 */
	private void initializeControls() {
		// OpenGL example.
		mGLSurfaceView = new ViewPentagons(this);
		RelativeLayout layoutBackground = findViewById(R.id.layout_background);
		layoutBackground.addView(mGLSurfaceView);

		// Temperature graphic.
		initializeTempPlot();

		// Other UI elements.
		TextView tvTemperatureWarning = findViewById(R.id.tv_temperature_warning);
		try {
			tvTemperatureWarning.setText(String.format(getResources().getString(R.string.temperature_warning),
					(int) cpuManager.getHotTemperature(), gpuManager.getMinMultiplier(), ((int) cpuManager.getHotTemperature() - 10)));
		} catch (Exception e) {
			e.printStackTrace();
		}

		tvFps = findViewById(R.id.tv_fps);
		tvCurrentTemperature = findViewById(R.id.tv_current_temperature);
		tvGpuMultiplier = findViewById(R.id.tv_gpu_multiplier);
		sbMultiplier = findViewById(R.id.sb_multiplier);

		int multiplier = MAX_MULTIPLIER;
		try {
			multiplier = gpuManager.getMultiplier();
		} catch (IOException e) {
			gpuMultError = true;
			Log.e(TAG, "Could not read the GPU multiplier");
			e.printStackTrace();
		} catch (UnsupportedOperationException e) {
			gpuMultError = true;
			Log.e(TAG, "Could not read the GPU multiplier");
			e.printStackTrace();
		}

		tvGpuMultiplier.setText(String.format(Locale.getDefault(), getResources().getString(R.string.gpu_multiplier), multiplier));
		sbMultiplier.setMax(MAX_MULTIPLIER - 1);
		sbMultiplier.setProgress(multiplier - 1);
		sbMultiplier.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
			@Override
			public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
				// Seek bar goes from 0 to 63, so add 1 to the given value.
				int newValue = i + 1;
				try {
					// Check if the multiplier was set correctly.
					if (gpuManager.setMultiplier(newValue) != newValue)
						// If not, set the minimum multiplier.
						seekBar.setProgress(gpuManager.getMinMultiplier() - 1);
					else
						tvGpuMultiplier.setText(String.format(getResources().getString(R.string.gpu_multiplier), newValue));
				} catch (IOException e) {
					Log.e(TAG, "Could not set the GPU multiplier");
					e.printStackTrace();
				} catch (UnsupportedOperationException e) {
					Log.e(TAG, "Could not set the GPU multiplier");
					e.printStackTrace();
				}
			}

			@Override
			public void onStartTrackingTouch(SeekBar seekBar) {
			}

			@Override
			public void onStopTrackingTouch(SeekBar seekBar) {
			}
		});
	}

	/**
	 * Initializes and configures the temperature plot.
	 */
	private void initializeTempPlot() {
		tempPlot = findViewById(R.id.temp_plot);

		tempPlot.setRangeBoundaries(40, 90, BoundaryMode.FIXED);
		tempPlot.setDomainBoundaries(0, 60, BoundaryMode.FIXED);
		tempPlot.setDomainStepValue(7);
		tempPlot.setDomainValueFormat(new DecimalFormat("0"));
		tempPlot.setRangeStepValue(6);
		tempPlot.setRangeValueFormat(new DecimalFormat("0"));

		try {
			tempPlot.addMarker(new YValueMarker(cpuManager.getHotTemperature(), ""));
		} catch (CPUTemperatureException e) {
			e.printStackTrace();
		}

		tempPlot.setPlotMargins(0, 0, 0, 0);
		tempPlot.setPlotPadding(0, 0, 0, 0);
		tempPlot.setGridPadding(0, 0, 0, 0);

		tempPlot.setBorderStyle(Plot.BorderStyle.NONE, null, null);
		tempPlot.setBackgroundColor(Color.TRANSPARENT);
		tempPlot.getGraphWidget().getBackgroundPaint().setColor(Color.TRANSPARENT);
		tempPlot.getGraphWidget().getGridBackgroundPaint().setColor(Color.TRANSPARENT);

		tempPlot.getGraphWidget().getDomainLabelPaint().setColor(Color.BLACK);
		tempPlot.getGraphWidget().getRangeLabelPaint().setColor(Color.BLACK);
		tempPlot.getGraphWidget().getDomainOriginLabelPaint().setColor(Color.BLACK);
		tempPlot.getGraphWidget().getDomainOriginLinePaint().setColor(Color.BLACK);
		tempPlot.getGraphWidget().getRangeOriginLabelPaint().setColor(Color.BLACK);
		tempPlot.getGraphWidget().getRangeOriginLinePaint().setColor(Color.BLACK);
		tempPlot.getGraphWidget().setPadding(0, 20, 20, 10);
		tempPlot.getGraphWidget().setMarginLeft(0);
		tempPlot.getGraphWidget().position(-0.5f, XLayoutStyle.RELATIVE_TO_RIGHT, -0.5f, YLayoutStyle.RELATIVE_TO_BOTTOM, AnchorPosition.CENTER);
		tempPlot.getGraphWidget().setSize(new SizeMetrics(0, SizeLayoutType.FILL, 0, SizeLayoutType.FILL));

		tempPlot.getLayoutManager().remove(tempPlot.getLegendWidget());
		tempPlot.getLayoutManager().remove(tempPlot.getTitleWidget());
		tempPlot.getLayoutManager().remove(tempPlot.getDomainLabelWidget());
		tempPlot.getLayoutManager().remove(tempPlot.getRangeLabelWidget());

		tempSeries = new SimpleXYSeries("Temperature (\u00b0C");
		tempSeries.useImplicitXVals();

		LineAndPointFormatter formatter = new LineAndPointFormatter(getResources().getColor(R.color.orange), null,
				getResources().getColor(R.color.orange), null);
		Paint paint = new Paint();
		paint.setAlpha(100);
		paint.setShader(new LinearGradient(0, 0, 0, 250, getResources().getColor(R.color.orange),
				getResources().getColor(R.color.orange), Shader.TileMode.MIRROR));
		formatter.setFillPaint(paint);

		tempPlot.addSeries(tempSeries, formatter);
	}
}
