/*
Copyright 2015 Alex Florescu
Copyright 2014 Stephan Tittel and Yahoo Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package com.jetradar.android.rangeseekbar;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.animation.AccelerateInterpolator;
import android.widget.ImageView;

/**
 * Widget that lets users select a minimum and maximum value on a given numerical range.
 * <br>
 * Improved {@link android.view.MotionEvent} handling for smoother use, anti-aliased painting for
 * improved aesthetics.
 *
 * @author Stephan Tittel (stephan.tittel@kom.tu-darmstadt.de)
 * @author Peter Sinnott (psinnott@gmail.com)
 * @author Thomas Barrasso (tbarrasso@sevenplusandroid.org)
 * @author Alex Florescu (alex@florescu.org)
 * @author Michael Keppler (bananeweizen@gmx.de)
 * @author Nikita Slushkin (nikita.slushkin@aviasales.ru)
 * @author Igor Yurchenko (igor.yurchenko@aviasales.ru)
 */
public class RangeSeekBar extends ImageView {
  private static final int INVALID_POINTER_ID = 255;
  // Localized constants from MotionEvent for compatibility
  // with API < 8 "Froyo".
  private static final int ACTION_POINTER_UP = 0x6, ACTION_POINTER_INDEX_MASK = 0x0000ff00, ACTION_POINTER_INDEX_SHIFT = 8;
  private static final int THUMB_ANIMATOR = 100;
  private static final String SAVE_SUPER = "SUPER";
  private static final String SAVE_MIN = "MIN";
  private static final String SAVE_MAX = "MAX";
  private static final float DEFAULT_MINIMUM = 0f;
  private static final float DEFAULT_MAXIMUM = 100f;
  private static final int HEIGHT_IN_DP = 60;
  private static final int DEFAULT_COLOR = Color.parseColor("#4FC3F7");
  private static final int DEFAULT_DISABLED_COLOR = Color.parseColor("#cacaca");
  private static final int DEFAULT_BACKGROUND_COLOR = Color.parseColor("#B9B9B9");
  private static final int THUMB_DEFAULT_RADIUS_DP = 6;
  private static final int THUMB_PRESSED_RADIUS_DP = 9;
  private static final int DEFAULT_DISTANCE_BETWEEN_THUMBS_CENTER_POINT_DP = THUMB_PRESSED_RADIUS_DP * 2;
  private static final int DOT_RADIUS_DP = 3;
  private static final int LEFT_RIGHT_PADDING_DP = 18;
  private static final int LINE_HEIGHT_DP = 2;
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final ValueAnimator mThumbAnimator = ValueAnimator.ofFloat(0, 1);
  private final ValueAnimator mRippleAnimator = ValueAnimator.ofFloat(0, 1);
  private float mLineHeight;
  private float mLeftRightPadding;
  private int mThumbDefaultRadius;
  private int mThumbPressedRadius;
  private int mBackColor;
  private int mProgressColor;
  private int mThumbColor;
  private int mDisabledColor;
  private int mThumbsMinDistance;
  private double mStepSize = DEFAULT_MAXIMUM - DEFAULT_MINIMUM;
  private RectF mRect;
  private double mAbsoluteMinValue, mAbsoluteMaxValue;
  private double mAbsoluteMinValuePrim, mAbsoluteMaxValuePrim;
  private double mNormalizedMinValue = 0d;
  private double mNormalizedMaxValue = 1d;
  private Thumb mPressedThumb = null;
  private boolean mNotifyWhileDragging = false;
  @Nullable private OnRangeSeekBarChangeListener mListener;
  private float mDownMotionX;
  private float mDownMotionY;
  private int mActivePointerId = INVALID_POINTER_ID;
  private int mScaledTouchSlop;
  private boolean mIsDragging;
  private boolean mSingleThumb;
  private boolean mAnimationStarted = false;
  private boolean mUseStepsWhileDragging = false;
  private boolean mShowStepsAsDots = false;
  private boolean mIgnoreMoveGesture = false;
  private boolean axisLocked = false;

  public RangeSeekBar(Context context) {
    super(context);
    init(null);
  }

  public RangeSeekBar(Context context, AttributeSet attrs) {
    super(context, attrs);
    init(attrs);
  }

  public RangeSeekBar(Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
    init(attrs);
  }

  private void init(AttributeSet attrs) {
    mThumbAnimator.setInterpolator(new AccelerateInterpolator());
    mThumbAnimator.setDuration(THUMB_ANIMATOR);
    TypedArray a = getContext().obtainStyledAttributes(attrs, R.styleable.RangeSeekBar, 0, 0);
    setRangeValues(
        a.getFloat(R.styleable.RangeSeekBar_absoluteMinValue, DEFAULT_MINIMUM),
        a.getFloat(R.styleable.RangeSeekBar_absoluteMaxValue, DEFAULT_MAXIMUM));
    mBackColor = a.getColor(R.styleable.RangeSeekBar_backgroundColor, DEFAULT_BACKGROUND_COLOR);
    mProgressColor = a.getColor(R.styleable.RangeSeekBar_progressColor, DEFAULT_COLOR);
    mThumbColor = a.getColor(R.styleable.RangeSeekBar_thumbColor, DEFAULT_COLOR);
    mSingleThumb = a.getBoolean(R.styleable.RangeSeekBar_singleThumb, false);
    mDisabledColor = a.getColor(R.styleable.RangeSeekBar_disabledColor, DEFAULT_DISABLED_COLOR);
    mThumbsMinDistance = a.getDimensionPixelOffset(R.styleable.RangeSeekBar_thumbsMinDistance, convertDPToPx(DEFAULT_DISTANCE_BETWEEN_THUMBS_CENTER_POINT_DP));
    a.recycle();

    setValuePrimAndNumberType();
    mLineHeight = convertDPToPx(LINE_HEIGHT_DP);
    mLeftRightPadding = convertDPToPx(LEFT_RIGHT_PADDING_DP);
    mThumbDefaultRadius = convertDPToPx(THUMB_DEFAULT_RADIUS_DP);
    mThumbPressedRadius = convertDPToPx(THUMB_PRESSED_RADIUS_DP);

    mRect = new RectF(0, 0, 0, 0);

    // make RangeSeekBar focusable. This solves focus handling issues in case EditText widgets are being used along with the RangeSeekBar within ScollViews.
    setFocusable(true);
    setFocusableInTouchMode(true);
    mScaledTouchSlop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
  }

  public void setRangeValues(double minValue, double maxValue) {
    this.mAbsoluteMinValue = minValue;
    this.mAbsoluteMaxValue = maxValue;
    mStepSize = mAbsoluteMaxValue - mAbsoluteMinValue;
    setValuePrimAndNumberType();
  }

  public void setSingleThumb(boolean singleThumb) {
    this.mSingleThumb = singleThumb;
  }

  public void setUseStepsWhileDragging(boolean useStepsWhileDragging) {
    this.mUseStepsWhileDragging = useStepsWhileDragging;
  }

  public void setShowStepsAsDots(boolean showStepsAsDots) {
    this.mShowStepsAsDots = showStepsAsDots;
  }

  public void setThumbsMinDistance(int thumbsMinDistance) {
    this.mThumbsMinDistance = thumbsMinDistance;
  }

  @SuppressWarnings("unchecked")
  // only used to set default values when initialised from XML without any values specified
  private void setRangeToDefaultValues() {
    this.mAbsoluteMinValue = DEFAULT_MINIMUM;
    this.mAbsoluteMaxValue = DEFAULT_MAXIMUM;
    mStepSize = mAbsoluteMaxValue - mAbsoluteMinValue;
    setValuePrimAndNumberType();
  }

  private void setValuePrimAndNumberType() {
    mAbsoluteMinValuePrim = mAbsoluteMinValue;
    mAbsoluteMaxValuePrim = mAbsoluteMaxValue;
  }

  public void resetSelectedValues() {
    setSelectedMinValue(mAbsoluteMinValue);
    setSelectedMaxValue(mAbsoluteMaxValue);
  }

  public boolean isNotifyWhileDragging() {
    return mNotifyWhileDragging;
  }

  /**
   * Should the widget notify the mListener callback while the user is still dragging a thumb?
   * Default is false.
   */
  public void setNotifyWhileDragging(boolean flag) {
    this.mNotifyWhileDragging = flag;
  }

  /**
   * Returns the absolute minimum value of the range that has been set at construction time.
   *
   * @return The absolute minimum value of the range.
   */
  public double getAbsoluteMinValue() {
    return mAbsoluteMinValue;
  }

  /**
   * Returns the absolute maximum value of the range that has been set at construction time.
   *
   * @return The absolute maximum value of the range.
   */
  public double getAbsoluteMaxValue() {
    return mAbsoluteMaxValue;
  }

  /**
   * Returns the currently selected min value.
   *
   * @return The currently selected min value.
   */
  public double getSelectedMinValue() {
    return normalizedToValue(mNormalizedMinValue);
  }

  /**
   * Sets the currently selected minimum value. The widget will be invalidated and redrawn.
   *
   * @param value The Number value to set the minimum value to. Will be clamped to given absolute
   * minimum/maximum range.
   */
  public void setSelectedMinValue(double value) {
    // in case mAbsoluteMinValue == mAbsoluteMaxValue, avoid division by zero when normalizing.
    if (0 == (mAbsoluteMaxValuePrim - mAbsoluteMinValuePrim)) {
      setNormalizedMinValue(0f);
    } else {
      setNormalizedMinValue(valueToNormalized(value));
    }
  }

  /**
   * Returns the currently selected max value.
   *
   * @return The currently selected max value.
   */
  public double getSelectedMaxValue() {
    return normalizedToValue(mNormalizedMaxValue);
  }

  /**
   * Sets the currently selected maximum value. The widget will be invalidated and redrawn.
   *
   * @param value The Number value to set the maximum value to. Will be clamped to given absolute
   * minimum/maximum range.
   */
  public void setSelectedMaxValue(double value) {
    // in case mAbsoluteMinValue == mAbsoluteMaxValue, avoid division by zero when normalizing.
    if (0 == (mAbsoluteMaxValuePrim - mAbsoluteMinValuePrim)) {
      setNormalizedMaxValue(1f);
    } else {
      setNormalizedMaxValue(valueToNormalized(value));
    }
  }

  /**
   * Registers given mListener callback to notify about changed selected values.
   *
   * @param listener The mListener to notify about changed selected values.
   */
  public void setOnRangeSeekBarChangeListener(OnRangeSeekBarChangeListener listener) {
    this.mListener = listener;
  }

  /**
   * Handles thumb selection and movement. Notifies mListener callback on certain events.
   */
  @Override
  public boolean onTouchEvent(MotionEvent event) {
    if (!isEnabled()) {
      return false;
    }

    int pointerIndex;
    float threshold = 1.0f;

    final int action = event.getAction();
    switch (action & MotionEvent.ACTION_MASK) {

      case MotionEvent.ACTION_DOWN:
        // Remember where the motion event started
        mActivePointerId = event.getPointerId(event.getPointerCount() - 1);
        pointerIndex = event.findPointerIndex(mActivePointerId);
        mDownMotionX = event.getX(pointerIndex);
        mDownMotionY = event.getY(pointerIndex);

        mIgnoreMoveGesture = false;
        break;
      case MotionEvent.ACTION_MOVE:
        if (!axisLocked) {

          float ratioLeftRight = Math.abs(event.getX() - mDownMotionX) / Math.abs(event.getY() - mDownMotionY);
          float ratioUpDown = Math.abs(event.getY() - mDownMotionY) / Math.abs(event.getX() - mDownMotionX);

          if (event.getY() < mDownMotionY && ratioUpDown > threshold) {
            mIgnoreMoveGesture = true;
            axisLocked = true;
            return false;
          } else if (event.getY() > mDownMotionY && ratioUpDown > threshold) {
            mIgnoreMoveGesture = true;
            axisLocked = true;
            return false;
          } else if (event.getX() < mDownMotionX && ratioLeftRight > threshold) {
            mIgnoreMoveGesture = false;
            axisLocked = true;
          } else if (event.getX() > mDownMotionX && ratioLeftRight > threshold) {
            mIgnoreMoveGesture = false;
            axisLocked = true;
          } else {
            mIgnoreMoveGesture = false;
            axisLocked = true;
          }

          mPressedThumb = evalPressedThumb(mDownMotionX);

          // Only handle thumb presses.
          if (mPressedThumb == null) {
            return super.onTouchEvent(event);
          }

          invalidate();
          onStartTrackingTouch();
          trackTouchEvent(event);
          attemptClaimDrag();
        }

        if (mIgnoreMoveGesture) {
          return false;
        }

        if (mPressedThumb != null) {
          setPressed(true);

          if (mIsDragging) {
            trackTouchEvent(event);
          } else {
            // Scroll to follow the motion event
            pointerIndex = event.findPointerIndex(mActivePointerId);
            final float x = event.getX(pointerIndex);

            if (Math.abs(x - mDownMotionX) > mScaledTouchSlop) {
              setPressed(true);
              invalidate();
              onStartTrackingTouch();
              trackTouchEvent(event);
              attemptClaimDrag();
            }
          }

          if (mNotifyWhileDragging && mListener != null) {
            mListener.onRangeSeekBarValuesChanged(this, getSelectedMinValue(), getSelectedMaxValue());
          }
        }
        break;
      case MotionEvent.ACTION_UP:
        if (mIsDragging) {
          trackTouchEvent(event);
          setPressed(false);
        } else {
          // Touch up when we never crossed the touch slop threshold
          // should be interpreted as a tap-seek to that location.
          if (!axisLocked && !mIgnoreMoveGesture) {
            mPressedThumb = evalPressedThumb(mDownMotionX);
          }
          onStartTrackingTouch();
          trackTouchEvent(event);
        }
        axisLocked = false;

        mPressedThumb = null;
        invalidate();
        if (mListener != null) {
          mListener.onRangeSeekBarValuesChanged(this, getSelectedMinValue(), getSelectedMaxValue());
        }
        onStopTrackingTouch();

        break;
      case MotionEvent.ACTION_POINTER_DOWN: {
        final int index = event.getPointerCount() - 1;
        // final int index = ev.getActionIndex();
        mDownMotionX = event.getX(index);
        mActivePointerId = event.getPointerId(index);
        invalidate();
        break;
      }
      case MotionEvent.ACTION_POINTER_UP:
        mPressedThumb = null;
        onSecondaryPointerUp(event);
        invalidate();
        break;
      case MotionEvent.ACTION_CANCEL:
        axisLocked = false;
        mPressedThumb = null;
        if (mIsDragging) {
          onStopTrackingTouch();
          setPressed(false);
        }
        invalidate(); // see above explanation
        break;
    }

    return true;
  }

  private void onSecondaryPointerUp(MotionEvent ev) {
    final int pointerIndex = (ev.getAction() & ACTION_POINTER_INDEX_MASK) >> ACTION_POINTER_INDEX_SHIFT;

    final int pointerId = ev.getPointerId(pointerIndex);
    if (pointerId == mActivePointerId) {
      // This was our active pointer going up. Choose
      // a new active pointer and adjust accordingly.
      // TODO: Make this decision more intelligent.
      final int newPointerIndex = pointerIndex == 0 ? 1 : 0;
      mDownMotionX = ev.getX(newPointerIndex);
      mActivePointerId = ev.getPointerId(newPointerIndex);
    }
  }

  private void trackTouchEvent(MotionEvent event) {
    final int pointerIndex = event.findPointerIndex(mActivePointerId);
    final float x = event.getX(pointerIndex);

    if (Thumb.UNKNOWN.equals(mPressedThumb)) {
      if (x < mDownMotionX) {
        mPressedThumb = Thumb.MIN;
      } else if (x > mDownMotionX) {
        mPressedThumb = Thumb.MAX;
      }
    }

    double screenToNormalizedValue = mUseStepsWhileDragging ? Math.round(screenToNormalized(x) * mStepSize) / mStepSize :
        screenToNormalized(x);

    if (Thumb.MIN.equals(mPressedThumb) && !mSingleThumb) {
      setNormalizedMinValue(screenToNormalizedValue);
    } else if (Thumb.MAX.equals(mPressedThumb)) {
      setNormalizedMaxValue(screenToNormalizedValue);
    }

    if (mListener != null) {
      mListener.onRangeSeekBarTracking(this, getSelectedMinValue(), getSelectedMaxValue());
    }
  }

  /**
   * Tries to claim the user's drag motion, and requests disallowing any ancestors from stealing
   * events in the drag.
   */
  private void attemptClaimDrag() {
    if (getParent() != null) {
      getParent().requestDisallowInterceptTouchEvent(true);
    }
  }

  /**
   * This is called when the user has started touching this widget.
   */
  private void onStartTrackingTouch() {
    mIsDragging = true;
  }

  /**
   * This is called when the user either releases his touch or the touch is canceled.
   */
  private void onStopTrackingTouch() {
    mIsDragging = false;
    mAnimationStarted = false;
    mRippleAnimator.cancel();
    mThumbAnimator.cancel();
    if (mListener != null) {
      mListener.onStopTrackingTouch();
    }
  }

  /**
   * Ensures correct size of the widget.
   */
  @Override
  protected synchronized void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    int width = 200;
    if (MeasureSpec.UNSPECIFIED != MeasureSpec.getMode(widthMeasureSpec)) {
      width = MeasureSpec.getSize(widthMeasureSpec);
    }

    int height = convertDPToPx(HEIGHT_IN_DP);
    if (MeasureSpec.UNSPECIFIED != MeasureSpec.getMode(heightMeasureSpec)) {
      height = Math.min(height, MeasureSpec.getSize(heightMeasureSpec));
    }
    setMeasuredDimension(width, height);
  }

  /**
   * Draws the widget on the given canvas.
   */
  @Override
  protected synchronized void onDraw(Canvas canvas) {
    super.onDraw(canvas);

    paint.setStyle(Paint.Style.FILL);
    paint.setColor(isEnabled() ? mBackColor : mDisabledColor);
    paint.setAntiAlias(true);

    // draw seek bar background line
    mRect.top = getHeight() / 2 - mLineHeight / 2;
    mRect.bottom = getHeight() / 2 + mLineHeight / 2;
    mRect.left = mLeftRightPadding;
    mRect.right = getWidth() - mLeftRightPadding;
    canvas.drawRect(mRect, paint);

    // draw seek bar active range line
    mRect.left = normalizedToScreen(mNormalizedMinValue);
    mRect.right = normalizedToScreen(mNormalizedMaxValue);

    paint.setColor(isEnabled() ? mProgressColor : mDisabledColor);
    canvas.drawRect(mRect, paint);

    if (mShowStepsAsDots) {
      drawDots(canvas);
    }

    // draw minimum thumb if not a single thumb control
    if (!mSingleThumb) {
      drawThumb(normalizedToScreen(mNormalizedMinValue), Thumb.MIN.equals(mPressedThumb), canvas);
    }

    // draw maximum thumb
    drawThumb(normalizedToScreen(mNormalizedMaxValue), Thumb.MAX.equals(mPressedThumb), canvas);
  }

  public void setProgressColor(int color) {
    mProgressColor = color;
    mThumbColor = color;
    paint.setColor(mProgressColor);
  }

  private void drawDots(Canvas canvas) {
    int dotsCount = (int) (mAbsoluteMaxValue - mAbsoluteMinValue);

    for (int i = 0; i < dotsCount + 1; i++) {
      float dotPosition = mRect.left + ((getWidth() - mLeftRightPadding) - mRect.left) / dotsCount * i;

      if (dotPosition <= normalizedToScreen(mNormalizedMaxValue) && dotPosition >= normalizedToScreen(mNormalizedMinValue)) {
        paint.setColor(mProgressColor);
      } else {
        paint.setColor(mBackColor);
      }
      canvas.drawCircle(dotPosition, getHeight() / 2, convertDPToPx(DOT_RADIUS_DP), paint);
    }
  }

  /**
   * Overridden to save instance state when device orientation changes. This method is called
   * automatically if
   * you assign an id to the RangeSeekBar widget using the {@link #setId(int)} method. Other
   * members
   * of this class
   * than the normalized min and max values don't need to be saved.
   */
  @Override
  protected Parcelable onSaveInstanceState() {
    final Bundle bundle = new Bundle();
    bundle.putParcelable(SAVE_SUPER, super.onSaveInstanceState());
    bundle.putDouble(SAVE_MIN, mNormalizedMinValue);
    bundle.putDouble(SAVE_MAX, mNormalizedMaxValue);
    return bundle;
  }

  /**
   * Overridden to restore instance state when device orientation changes. This method is called
   * automatically if you
   * assign an id to the RangeSeekBar widget using the {@link #setId(int)} method.
   */
  @Override
  protected void onRestoreInstanceState(Parcelable parcel) {
    final Bundle bundle = (Bundle) parcel;
    super.onRestoreInstanceState(bundle.getParcelable(SAVE_SUPER));
    mNormalizedMinValue = bundle.getDouble(SAVE_MIN);
    mNormalizedMaxValue = bundle.getDouble(SAVE_MAX);
  }

  /**
   * Draws the "normal" resp. "pressed" thumb image on specified x-coordinate.
   *
   * @param screenCoord The x-coordinate in screen space where to draw the image.
   * @param pressed Is the thumb currently in "pressed" state?
   * @param canvas The canvas to draw upon.
   */
  private void drawThumb(float screenCoord, boolean pressed, Canvas canvas) {
    int radius = mThumbDefaultRadius;
    if (isPressed() && pressed && !mAnimationStarted) {
      mAnimationStarted = true;
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        mRippleAnimator.start();
      }
      mThumbAnimator.cancel();
      mThumbAnimator.setFloatValues(0, 1);
      mThumbAnimator.start();
    }
    if (isPressed() && pressed) {
      radius += (int) ((Float) mThumbAnimator.getAnimatedValue() * (mThumbPressedRadius - mThumbDefaultRadius));
    }
    paint.setColor(isEnabled() ? mThumbColor : mDisabledColor);
    canvas.drawCircle(screenCoord, getHeight() / 2, radius, paint);
  }

  /**
   * Decides which (if any) thumb is touched by the given x-coordinate.
   *
   * @param touchX The x-coordinate of a touch event in screen space.
   * @return The pressed thumb or null if none has been touched.
   */
  private Thumb evalPressedThumb(float touchX) {
    if (mSingleThumb) return Thumb.MAX;

    double minDistance = touchToTumbDistance(touchX, mNormalizedMinValue);
    double maxDistance = touchToTumbDistance(touchX, mNormalizedMaxValue);

    if (thumbsInOnePoint()) {
      if (1d == mNormalizedMaxValue) {
        return Thumb.MIN;
      }
      if (0d == mNormalizedMinValue) {
        return Thumb.MAX;
      } else {
        return Thumb.UNKNOWN;
      }
    } else if (minDistance < maxDistance) {
      return Thumb.MIN;
    } else {
      return Thumb.MAX;
    }
  }

  private boolean thumbsInOnePoint() {
    return mNormalizedMaxValue == mNormalizedMinValue;
  }

  /**
   * Decides if given x-coordinate in screen space needs to be interpreted as "within" the
   * normalized thumb x-coordinate.
   *
   * @param touchX The x-coordinate in screen space to check.
   * @param normalizedThumbValue The normalized x-coordinate of the thumb to check.
   * @return true if x-coordinate is in thumb range, false otherwise.
   */
  private double touchToTumbDistance(double touchX, double normalizedThumbValue) {
    return Math.abs(touchX - normalizedToScreen(normalizedThumbValue));
  }

  /**
   * Sets normalized min value to value so that 0 <= value <= normalized max value <= 1. The View
   * will get invalidated when calling this method.
   *
   * @param value The new normalized min value to set.
   */
  private void setNormalizedMinValue(double value) {
    mNormalizedMinValue = Math.max(0d, Math.min(1d, Math.min(value,
        screenToNormalized(normalizedToScreen(mNormalizedMaxValue) -
            mThumbsMinDistance))));
    invalidate();
  }

  /**
   * Sets normalized max value to value so that 0 <= normalized min value <= value <= 1. The View
   * will get invalidated when calling this method.
   *
   * @param value The new normalized max value to set.
   */
  private void setNormalizedMaxValue(double value) {
    mNormalizedMaxValue = Math.max(0d, Math.min(1d, Math.max(value,
        mSingleThumb
            ? mNormalizedMinValue
            : screenToNormalized(normalizedToScreen(mNormalizedMinValue) + mThumbsMinDistance))));
    invalidate();
  }

  /**
   * Converts a normalized value to a Number object in the value space between absolute minimum and
   * maximum.
   */
  @SuppressWarnings("unchecked")
  private double normalizedToValue(double normalized) {
    double v = mAbsoluteMinValuePrim + normalized * (mAbsoluteMaxValuePrim - mAbsoluteMinValuePrim);
    return Math.round(v * 100) / 100d;
  }

  /**
   * Converts the given Number value to a normalized double.
   *
   * @param value The Number value to normalize.
   * @return The normalized double.
   */
  private double valueToNormalized(double value) {
    if (0 == mAbsoluteMaxValuePrim - mAbsoluteMinValuePrim) {
      return 0d;
    }
    return (value - mAbsoluteMinValuePrim) / (mAbsoluteMaxValuePrim - mAbsoluteMinValuePrim);
  }

  /**
   * Converts a normalized value into screen space.
   *
   * @param normalizedCoord The normalized value to convert.
   * @return The converted value in screen space.
   */
  private float normalizedToScreen(double normalizedCoord) {
    return (float) (mLeftRightPadding + normalizedCoord * (getWidth() - 2 * mLeftRightPadding));
  }

  /**
   * Converts screen space x-coordinates into normalized values.
   *
   * @param screenCoord The x-coordinate in screen space to convert.
   * @return The normalized value.
   */
  private double screenToNormalized(double screenCoord) {
    int width = getWidth();
    if (width <= 2 * mLeftRightPadding) {
      // prevent division by zero, simply return 0.
      return 0d;
    } else {
      double result = (screenCoord - mLeftRightPadding) / (width - 2 * mLeftRightPadding);
      return Math.min(1d, Math.max(0d, result));
    }
  }


  private int convertDPToPx(int dpValue) {
    return (int) TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, dpValue, getContext().getResources().getDisplayMetrics());
  }



  /**
   * Thumb constants (min and max).
   */
  private enum Thumb {
    MIN, MAX, UNKNOWN
  }

  public interface OnRangeSeekBarChangeListener {
    void onRangeSeekBarValuesChanged(RangeSeekBar bar, Double minValue, Double maxValue);

    void onStopTrackingTouch();

    void onRangeSeekBarTracking(RangeSeekBar tRangeSeekBar, Double selectedMinValue, Double selectedMaxValue);
  }
}