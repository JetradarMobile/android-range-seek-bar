package com.jetradar.android.rangeseekbar;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;

@RunWith(RobolectricGradleTestRunner.class)
@Config(constants = BuildConfig.class, sdk = 21)
public class RangeSeekBarTest {

    @Test
    public void rsb_should_handle_long_values() {
        RangeSeekBar mSeekBar = new RangeSeekBar(RuntimeEnvironment.application);
        // Set up the seek bar
        mSeekBar.setRangeValues(0L, 100L);
        double minValue = mSeekBar.getAbsoluteMinValue();
        assertEquals(0d, minValue, 0);
        double maxValue = mSeekBar.getAbsoluteMaxValue();
        assertEquals(100d, maxValue, 0);
    }

}