/**
 * 
 * Funf: Open Sensing Framework
 * Copyright (C) 2010-2011 Nadav Aharony, Wei Pan, Alex Pentland.
 * Acknowledgments: Alan Gardner
 * Contact: nadav@media.mit.edu
 * 
 * This file is part of Funf.
 * 
 * Funf is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 * 
 * Funf is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with Funf. If not, see <http://www.gnu.org/licenses/>.
 * 
 * Last edited by Fuming Shih at Aug 7, 2012
 * 
 */
package edu.mit.media.funf.probe.builtin;


import java.math.BigDecimal;

import android.os.AsyncTask;
import android.os.Debug;
import android.util.Log;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import edu.mit.media.funf.Schedule;
import edu.mit.media.funf.Schedule.DefaultSchedule;
import edu.mit.media.funf.config.Configurable;
import edu.mit.media.funf.json.IJsonObject;
import edu.mit.media.funf.probe.Probe.Base;
import edu.mit.media.funf.probe.Probe.ContinuousProbe;
import edu.mit.media.funf.probe.Probe.PassiveProbe;
import edu.mit.media.funf.probe.Probe.RequiredFeatures;
import edu.mit.media.funf.probe.Probe.RequiredProbes;
import edu.mit.media.funf.probe.builtin.ProbeKeys.ActivityKeys;
import edu.mit.media.funf.time.TimeUtil;
import edu.mit.media.funf.util.LogUtil;

@Schedule.DefaultSchedule(interval=120, duration=15)
@RequiredFeatures("android.hardware.sensor.accelerometer")
@RequiredProbes(AccelerometerSensorProbe.class)
public class ActivityProbe extends Base implements ContinuousProbe, PassiveProbe, ActivityKeys {

	@Configurable
	private double interval = 1.0;
	private static final long INTERVAL = 1L;
	
	private double startTime;
	private int intervalCount;
	private int lowActivityIntervalCount;
	private int highActivityIntervalCount;
	
	private ActivityCounter activityCounter = new ActivityCounter();

	@Override
	protected void onStart() {
		super.onStart();
		getAccelerometerProbe().registerListener(activityCounter);
	}

	@Override
	protected void onStop() {
		getAccelerometerProbe().unregisterListener(activityCounter);
		super.onStop();
	}

	private AccelerometerSensorProbe getAccelerometerProbe() {
		return getGson().fromJson("{ 'sensorDelay': 3 }", AccelerometerSensorProbe.class);
	}

	private class ActivityCounter implements DataListener {
		private double intervalStartTime;
		private float varianceSum;
		private float avg;
		private float sum;
		private int count;
		
		private void reset(double timestamp) {
			// If more than an interval away, start a new scan
			varianceSum = avg = sum = count = 0;
			startTime = intervalStartTime = timestamp;
			varianceSum = avg = sum = count = 0;
			intervalCount = 1;
			lowActivityIntervalCount = 0;
			highActivityIntervalCount = 0;
		}
		
		private void intervalReset() {
			Log.d(LogUtil.TAG, "interval RESET");
			// Calculate activity and reset
			intervalCount++;
			JsonObject data = new JsonObject();
			if (varianceSum >= 10.0f) {
				data.addProperty(ACTIVITY_LEVEL, ACTIVITY_LEVEL_HIGH);
			} else if (varianceSum < 10.0f && varianceSum > 3.0f) {
				data.addProperty(ACTIVITY_LEVEL, ACTIVITY_LEVEL_LOW);
			} else {
				data.addProperty(ACTIVITY_LEVEL, ACTIVITY_LEVEL_NONE);
			}
			sendData(data);
			intervalStartTime += INTERVAL; // Ensure 1 second intervals
			varianceSum = avg = sum = count = 0;
		}
		
		private void update(float x, float y, float z) {
			//Log.d(TAG, "UPDATE:(" + x + "," + y + "," + z + ")");
			// Iteratively calculate variance sum
			count++;
			float magnitude = (float)Math.sqrt(x*x + y*y + z*z);
			float newAvg = (count - 1)*avg/count + magnitude/count;
			float deltaAvg = newAvg - avg;
			varianceSum += (magnitude - newAvg) * (magnitude - newAvg)
				- 2*(sum - (count-1)*avg) 
				+ (count - 1) *(deltaAvg * deltaAvg);
			sum += magnitude;
			avg = newAvg;
			//Log.d(TAG, "UPDATED VALUES:(count, varianceSum, sum, avg) " + count + ", " + varianceSum+ ", " + sum+ ", " + avg);
		}
		

		@Override
		public void onDataReceived(IJsonObject completeProbeUri, final IJsonObject data) {
			double timestamp = data.get(TIMESTAMP).getAsDouble();
			Log.d(LogUtil.TAG, "Starttime: " + startTime + " intervalStartTime: " + intervalStartTime);
			Log.d(LogUtil.TAG, "RECEIVED:" + timestamp);
			if (timestamp >= intervalStartTime + 2 * interval) {
				Log.d(LogUtil.TAG, "RESET:" + timestamp);
				reset(timestamp);
			} else if (timestamp >= intervalStartTime + interval) {
				Log.d(LogUtil.TAG, "interval Reset:" + timestamp);
				intervalReset();
			}
			float x = data.get(AccelerometerSensorProbe.X).getAsFloat();
			float y = data.get(AccelerometerSensorProbe.Y).getAsFloat();
			float z = data.get(AccelerometerSensorProbe.Z).getAsFloat();
			update(x, y, z);
		}

		@Override
		
		public void onDataCompleted(IJsonObject completeProbeUri, JsonElement checkpoint) {
			// Do nothing
		}
	}
}
