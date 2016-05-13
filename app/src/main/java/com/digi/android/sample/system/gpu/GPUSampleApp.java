/**
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
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Shader;
import android.os.Bundle;
import android.util.Log;
import android.view.WindowManager.LayoutParams;
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
import com.digi.android.sample.system.gpu.opengl.CubeGLSurfaceView;
import com.digi.android.sample.system.gpu.opengl.DragControl;
import com.digi.android.system.cpu.CPUManager;
import com.digi.android.system.cpu.ICPUTemperatureListener;
import com.digi.android.system.gpu.GPUManager;

import java.io.IOException;
import java.text.DecimalFormat;

/**
 * GPU sample application.
 *
 * <p>This example lets you adjust the GPU multiplier and rotate an OpenGL cube
 * while monitoring the module's temperature.</p>
 *
 * <p>For a complete description on the example, refer to the 'README.md' file
 * included in the example directory.</p>
 */
public class GPUSampleApp extends Activity {

	// Constants.
	public static final String TAG = "GPUSampleApp";

	private static final int TEMPERATURE_INTERVAL = 1000;
	private static final int MAX_TIME = 60;
	private static final int MAX_MULTIPLIER = 64;

	// Variables.
	private CPUManager cpuManager;
	private GPUManager gpuManager;

	private TextView tvCurrentTemperature;
	private TextView tvGpuMultiplier;

	private XYPlot tempPlot;
	private SimpleXYSeries tempSeries;

	private ICPUTemperatureListener temperatureListener = new ICPUTemperatureListener() {
		@Override
		public void onTemperatureUpdate(float temperature) {
			if (tempSeries.size() > MAX_TIME)
				tempSeries.removeFirst();
			tempSeries.addLast(null, temperature);
			tempPlot.redraw();

			tvCurrentTemperature.setText(String.format(getResources().getString(R.string.current_temperature), (int)temperature));
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
		cpuManager.registerListener(temperatureListener, TEMPERATURE_INTERVAL);
	}

	@Override
	protected void onPause() {
		super.onPause();
		cpuManager.unregisterListener(temperatureListener);
	}

	/**
	 * Initializes application controls.
	 */
	private void initializeControls() {
		// OpenGL rotating cube.
		RelativeLayout layoutBackground = (RelativeLayout) findViewById(R.id.layout_background);
		CubeGLSurfaceView glView = new CubeGLSurfaceView(this);
		layoutBackground.addView(glView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
		DragControl dragControl = new DragControl(this);
		glView.setOnTouchListener(dragControl);
		glView.setDragControl(dragControl);
		glView.getHolder().setFormat(PixelFormat.TRANSLUCENT | LayoutParams.FLAG_BLUR_BEHIND);
		glView.setZOrderOnTop(true);
		dragControl.setFD(1);

		// Temperature graphic.
		initializeTempPlot();

		// Other UI elements.
		tvCurrentTemperature = (TextView) findViewById(R.id.tv_current_temperature);
		tvGpuMultiplier = (TextView) findViewById(R.id.tv_gpu_multiplier);
		SeekBar sbMultiplier = (SeekBar) findViewById(R.id.sb_multiplier);

		int multiplier = MAX_MULTIPLIER;
		try {
			multiplier = gpuManager.getMultiplier();
		} catch (IOException e) {
			Log.e(TAG, "Could not read the GPU multiplier");
			e.printStackTrace();
		}

		tvGpuMultiplier.setText(String.format(getResources().getString(R.string.gpu_multiplier), multiplier));
		sbMultiplier.setMax(MAX_MULTIPLIER);
		sbMultiplier.setProgress(multiplier);
		sbMultiplier.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
			@Override
			public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
				int newValue = i > 0 ? i : 1;
				try {
					gpuManager.setMultiplier(newValue);
					tvGpuMultiplier.setText(String.format(getResources().getString(R.string.gpu_multiplier), newValue));
				} catch (IOException e) {
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
		tempPlot = (XYPlot) findViewById(R.id.temp_plot);

		tempPlot.setRangeBoundaries(50, 90, BoundaryMode.FIXED);
		tempPlot.setDomainBoundaries(0, 60, BoundaryMode.FIXED);
		tempPlot.setDomainStepValue(7);
		tempPlot.setDomainValueFormat(new DecimalFormat("0"));
		tempPlot.setRangeStepValue(9);
		tempPlot.setRangeValueFormat(new DecimalFormat("0"));

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
