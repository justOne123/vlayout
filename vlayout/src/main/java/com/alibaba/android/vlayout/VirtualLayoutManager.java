package com.alibaba.android.vlayout;

import android.content.Context;
import android.graphics.Rect;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.OrientationHelper;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget._ExposeLinearLayoutManagerEx;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import com.alibaba.android.vlayout.layout.DefaultLayoutHelper;

import java.util.LinkedList;
import java.util.List;


/**
 * A {@link android.support.v7.widget.RecyclerView.LayoutManager} implementation which provides
 * a virtual layout for actual views
 *
 * @author villadora
 * @since 1.0.0
 */

public class VirtualLayoutManager extends _ExposeLinearLayoutManagerEx implements LayoutManagerHelper {
    private static final String TAG = "VirtualLayoutManager";

    private static final boolean DEBUG = false;


    public static final int HORIZONTAL = OrientationHelper.HORIZONTAL;

    public static final int VERTICAL = OrientationHelper.VERTICAL;


    private OrientationHelper mOrientationHelper;
    private OrientationHelper mSecondaryOrientationHelper;

    private RecyclerView mRecyclerView;


    public VirtualLayoutManager(@NonNull final Context context) {
        this(context, VERTICAL);
    }

    public VirtualLayoutManager(@NonNull final Context context, int orientation) {
        this(context, orientation, false);
    }

    /**
     * @param context     Current context, will be used to access resources.
     * @param orientation Layout orientation. Should be {@link #HORIZONTAL} or {@link
     *                    #VERTICAL}.
     */
    public VirtualLayoutManager(@NonNull final Context context, int orientation, boolean reverseLayout) {
        super(context, orientation, reverseLayout);
        this.mOrientationHelper = OrientationHelper.createOrientationHelper(this, orientation);
        this.mSecondaryOrientationHelper = OrientationHelper.createOrientationHelper(this, orientation == VERTICAL ? VERTICAL : HORIZONTAL);
        setHelperFinder(new RangeLayoutHelperFinder());
    }


    private LayoutHelperFinder mHelperFinder;

    public void setHelperFinder(@NonNull final LayoutHelperFinder finder) {
        //noinspection ConstantConditions
        if (finder == null) {
            throw new IllegalArgumentException("finder is null");
        }

        List<LayoutHelper> helpers = new LinkedList<>();
        if (this.mHelperFinder != null) {
            for (LayoutHelper helper : mHelperFinder) {
                helpers.add(helper);
            }
        }

        this.mHelperFinder = finder;
        if (helpers.size() > 0)
            this.mHelperFinder.setLayouts(helpers);

        requestLayout();
    }

    public void setLayoutHelpers(@Nullable List<LayoutHelper> helpers) {
        // SparseArray<LayoutHelper> newHelpersSet = new SparseArray<>();
        // newHelpersSet.put(System.identityHashCode(helper), helper);

        for (LayoutHelper helper : mHelperFinder) {
            helper.clear(this);
        }

        // set ranges
        if (helpers != null) {
            int start = 0;
            for (int i = 0; i < helpers.size(); i++) {
                LayoutHelper helper = helpers.get(i);
                if (helper.getItemCount() > 0) {
                    helper.setRange(start, start + helper.getItemCount() - 1);
                } else {
                    helper.setRange(-1, -1);
                }

                start += helper.getItemCount();
            }
        }

        this.mHelperFinder.setLayouts(helpers);
        requestLayout();
    }


    @NonNull
    public List<LayoutHelper> getLayoutHelpers() {
        return this.mHelperFinder.getLayoutHelpers();
    }

    /**
     * Either be {@link #HORIZONTAL} or {@link #VERTICAL}
     *
     * @return orientation of this layout manager
     */
    @Override
    public int getOrientation() {
        return super.getOrientation();
    }

    @Override
    public void setOrientation(int orientation) {
        this.mOrientationHelper = OrientationHelper.createOrientationHelper(this, orientation);
        super.setOrientation(orientation);
    }

    /**
     * reverseLayout is not supported by VirtualLayoutManager. It's get disabled until all the LayoutHelpers support it.
     */
    @Override
    public void setReverseLayout(boolean reverseLayout) {
        if (reverseLayout) {
            throw new UnsupportedOperationException(
                    "VirtualLayoutManager does not support reverse layout in current version.");
        }

        super.setReverseLayout(false);
    }

    /**
     * stackFromEnd is not supported by VirtualLayoutManager. It's get disabled util all the layoutHelpers support it.
     * {@link #setReverseLayout(boolean)}.
     */
    @Override
    public void setStackFromEnd(boolean stackFromEnd) {
        if (stackFromEnd) {
            throw new UnsupportedOperationException(
                    "VirtualLayoutManager does not support stack from end.");
        }
        super.setStackFromEnd(false);
    }


    private AnchorInfoWrapper mTempAnchorInfoWrapper = new AnchorInfoWrapper();

    @Override
    public void onAnchorReady(RecyclerView.State state, _ExposeLinearLayoutManagerEx.AnchorInfo anchorInfo) {
        super.onAnchorReady(state, anchorInfo);

        boolean changed = true;
        while (changed) {
            mTempAnchorInfoWrapper.position = anchorInfo.mPosition;
            mTempAnchorInfoWrapper.coordinate = anchorInfo.mCoordinate;
            mTempAnchorInfoWrapper.layoutFromEnd = anchorInfo.mLayoutFromEnd;
            LayoutHelper layoutHelper = mHelperFinder.getLayoutHelper(anchorInfo.mPosition);
            if (layoutHelper != null)
                layoutHelper.checkAnchorInfo(state, mTempAnchorInfoWrapper, this);

            if (mTempAnchorInfoWrapper.position == anchorInfo.mPosition) {
                changed = false;
            } else {
                anchorInfo.mPosition = mTempAnchorInfoWrapper.position;
                anchorInfo.mCoordinate = mTempAnchorInfoWrapper.coordinate;
            }

            mTempAnchorInfoWrapper.position = -1;
        }


        mTempAnchorInfoWrapper.position = anchorInfo.mPosition;
        mTempAnchorInfoWrapper.coordinate = anchorInfo.mCoordinate;
        for (LayoutHelper layoutHelper : mHelperFinder) {
            layoutHelper.onRefreshLayout(state, mTempAnchorInfoWrapper, this);
        }
    }

    @Override
    protected int getExtraMargin(View child, boolean isLayoutEnd) {
        int position = getPosition(child);
        if (position != RecyclerView.NO_POSITION) {
            LayoutHelper helper = mHelperFinder.getLayoutHelper(position);
            if (helper != null) {
                return helper.getExtraMargin(position - helper.getRange().getLower(), child,
                        isLayoutEnd, getOrientation() == VERTICAL, this);
            }
        }

        return 0;
    }

    private int mNested = 0;

    private void runPreLayout(RecyclerView.Recycler recycler, RecyclerView.State state) {

        if (mNested == 0) {
            for (LayoutHelper layoutHelper : mHelperFinder) {
                layoutHelper.beforeLayout(recycler, state, this);
            }
        }

        mNested++;
    }

    private void runPostLayout(RecyclerView.Recycler recycler, RecyclerView.State state, int scrolled) {
        mNested--;
        if (mNested <= 0) {
            mNested = 0;
            final int startPosition = findFirstVisibleItemPosition();
            final int endPosition = findLastVisibleItemPosition();
            for (LayoutHelper layoutHelper : mHelperFinder) {
                layoutHelper.afterLayout(recycler, state, startPosition, endPosition, scrolled, this);
            }
        }
    }


    @Override
    public void onLayoutChildren(RecyclerView.Recycler recycler, RecyclerView.State state) {

        runPreLayout(recycler, state);

        super.onLayoutChildren(recycler, state);

        runPostLayout(recycler, state, Integer.MAX_VALUE); // hack to indicate its an initial layout
    }

    @Override
    protected int scrollBy(int dy, RecyclerView.Recycler recycler, RecyclerView.State state) {
        // detach fixedPositions
        runPreLayout(recycler, state);

        final int scrolled = super.scrollBy(dy, recycler, state);

        // attach fixedPositions
        runPostLayout(recycler, state, scrolled);


        return scrolled;
    }

    @Override
    public void onScrollStateChanged(int state) {
        super.onScrollStateChanged(state);

        for (LayoutHelper helper : mHelperFinder) {
            helper.onScrollStateChanged(state, this);
        }
    }

    @Override
    public void offsetChildrenHorizontal(int dx) {
        super.offsetChildrenHorizontal(dx);

        for (LayoutHelper helper : mHelperFinder) {
            helper.offsetChildrenHorizontal(dx, this);
        }
    }

    @Override
    public void offsetChildrenVertical(int dy) {
        super.offsetChildrenVertical(dy);
        for (LayoutHelper helper : mHelperFinder) {
            helper.offsetChildrenVertical(dy, this);
        }
    }

    private LayoutStateWrapper mTempLayoutStateWrapper = new LayoutStateWrapper();

    @Override
    protected void layoutChunk(RecyclerView.Recycler recycler, RecyclerView.State state, LayoutState layoutState, com.alibaba.android.vlayout.layout.LayoutChunkResult result) {
        final int position = layoutState.mCurrentPosition;
        mTempLayoutStateWrapper.mLayoutState = layoutState;
        LayoutHelper layoutHelper = mHelperFinder == null ? null : mHelperFinder.getLayoutHelper(position);
        if (layoutHelper == null)
            layoutHelper = mDefaultLayoutHelper;

        layoutHelper.doLayout(recycler, state, mTempLayoutStateWrapper, result, this);

        mTempLayoutStateWrapper.mLayoutState = null;


        // no item consumed
        if (layoutState.mCurrentPosition == position) {
            // break as no item consumed
            result.mFinished = true;
        }
    }


    private static LayoutHelper DEFAULT_LAYOUT_HELPER = new DefaultLayoutHelper();

    private LayoutHelper mDefaultLayoutHelper = DEFAULT_LAYOUT_HELPER;

    /**
     * Change default LayoutHelper
     *
     * @param layoutHelper default layoutHelper apply to items without specified layoutHelper, it should not be null
     */
    private void setDefaultLayoutHelper(@NonNull final LayoutHelper layoutHelper) {
        //noinspection ConstantConditions
        if (layoutHelper == null)
            throw new IllegalArgumentException("layoutHelper should not be null");

        this.mDefaultLayoutHelper = layoutHelper;
    }

    @Override
    public void scrollToPosition(int position) {
        super.scrollToPosition(position);
    }


    @Override
    public void scrollToPositionWithOffset(int position, int offset) {
        super.scrollToPositionWithOffset(position, offset);
    }

    @Override
    public void smoothScrollToPosition(RecyclerView recyclerView, RecyclerView.State state, int position) {
        super.smoothScrollToPosition(recyclerView, state, position);
    }


    @Override
    public boolean supportsPredictiveItemAnimations() {
        return mPendingSavedState == null;
    }


    /**
     * Do updates when items change
     *
     * @param recyclerView  recyclerView that belong to
     * @param positionStart start position that items changed
     * @param itemCount     number of items that changed
     */
    @Override
    public void onItemsAdded(RecyclerView recyclerView, int positionStart, int itemCount) {
        onItemsChanged(recyclerView);
    }

    @Override
    public void onItemsRemoved(RecyclerView recyclerView, int positionStart, int itemCount) {
        onItemsChanged(recyclerView);
    }

    @Override
    public void onItemsUpdated(RecyclerView recyclerView, int positionStart, int itemCount) {
        onItemsChanged(recyclerView);
    }

    @Override
    public void onItemsMoved(RecyclerView recyclerView, int from, int to, int itemCount) {
        onItemsChanged(recyclerView);
    }

    @Override
    public void onItemsChanged(RecyclerView recyclerView) {
        // TODO: do update

        for (LayoutHelper helper : mHelperFinder) {
            helper.onItemsChanged(this);
        }
    }


    @Override
    public boolean checkLayoutParams(RecyclerView.LayoutParams lp) {
        return lp instanceof LayoutParams;
    }

    @Override
    public RecyclerView.LayoutParams generateDefaultLayoutParams() {
        return new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    @Override
    public RecyclerView.LayoutParams generateLayoutParams(ViewGroup.LayoutParams lp) {
        if (lp instanceof LayoutParams) {
            return new LayoutParams((LayoutParams) lp);
        } else if (lp instanceof ViewGroup.MarginLayoutParams) {
            return new LayoutParams((ViewGroup.MarginLayoutParams) lp);
        } else {
            return new LayoutParams(lp);
        }
    }

    @Override
    public RecyclerView.LayoutParams generateLayoutParams(Context c, AttributeSet attrs) {
        return new LayoutParams(c, attrs);
    }


    /**
     * Considering margins in {@link LayoutParams} when doing calculation
     */
    @Override
    public int getDecoratedMeasuredWidth(View child) {
        final ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) child.getLayoutParams();
        return super.getDecoratedMeasuredWidth(child) + lp.leftMargin + lp.rightMargin;
    }

    @Override
    public int getDecoratedMeasuredHeight(View child) {
        final ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) child.getLayoutParams();
        return super.getDecoratedMeasuredHeight(child) + lp.topMargin + lp.bottomMargin;
    }

    @Override
    public int getDecoratedLeft(View child) {
        final ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) child.getLayoutParams();
        return super.getDecoratedLeft(child) - lp.leftMargin;
    }

    @Override
    public int getDecoratedTop(View child) {
        final ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) child.getLayoutParams();
        return super.getDecoratedTop(child) - lp.topMargin;
    }

    @Override
    public int getDecoratedRight(View child) {
        final ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) child.getLayoutParams();
        return super.getDecoratedRight(child) + lp.rightMargin;
    }

    @Override
    public int getDecoratedBottom(View child) {
        final ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) child.getLayoutParams();
        return super.getDecoratedBottom(child) + lp.bottomMargin;
    }

    @Override
    public void layoutDecorated(View child, int left, int top, int right, int bottom) {
        final ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) child.getLayoutParams();
        super.layoutDecorated(child, left + lp.leftMargin, top + lp.topMargin,
                right - lp.rightMargin, bottom - lp.bottomMargin);
    }

    @Override
    public void onAdapterChanged(RecyclerView.Adapter oldAdapter, RecyclerView.Adapter newAdapter) {
        super.onAdapterChanged(oldAdapter, newAdapter);
    }


    private RecyclerView.OnItemTouchListener mOnItemTouchListener = new RecyclerView.OnItemTouchListener() {
        @Override
        public boolean onInterceptTouchEvent(RecyclerView rv, MotionEvent e) {
            return false;
        }

        @Override
        public void onTouchEvent(RecyclerView rv, MotionEvent e) {

        }
    };

    @Override
    public void onAttachedToWindow(RecyclerView view) {
        super.onAttachedToWindow(view);
        mRecyclerView = view;
        mRecyclerView.addOnItemTouchListener(mOnItemTouchListener);
    }

    @Override
    public void onDetachedFromWindow(RecyclerView view, RecyclerView.Recycler recycler) {
        super.onDetachedFromWindow(view, recycler);

        for (LayoutHelper helper : mHelperFinder) {
            helper.clear(this);
        }

        mRecyclerView.removeOnItemTouchListener(mOnItemTouchListener);
        mRecyclerView = null;
    }

    @SuppressWarnings("unused")
    public static class LayoutParams extends RecyclerView.LayoutParams {


        public static final int NORMAL = 0;

        public static final int PLACE_ABOVE = 1;

        public static final int PLACE_BELOW = 2;

        public int positionType = NORMAL;

        public LayoutParams(Context c, AttributeSet attrs) {
            super(c, attrs);
        }

        public LayoutParams(int positionType, int width, int height) {
            super(width, height);
            this.positionType = positionType;
        }

        public LayoutParams(int width, int height) {
            super(width, height);
        }

        public LayoutParams(ViewGroup.MarginLayoutParams source) {
            super(source);
        }

        public LayoutParams(ViewGroup.LayoutParams source) {
            super(source);
        }

        public LayoutParams(RecyclerView.LayoutParams source) {
            super(source);
        }

    }


    public static class AnchorInfoWrapper {

        public int position;

        public int coordinate;

        public boolean layoutFromEnd;

        AnchorInfoWrapper() {

        }

    }


    @SuppressWarnings({"JavaDoc", "unused"})
    public static class LayoutStateWrapper {
        public final static int LAYOUT_START = -1;

        public final static int LAYOUT_END = 1;

        final static int INVALID_LAYOUT = Integer.MIN_VALUE;

        public final static int ITEM_DIRECTION_HEAD = -1;

        public final static int ITEM_DIRECTION_TAIL = 1;

        final static int SCOLLING_OFFSET_NaN = Integer.MIN_VALUE;

        private LayoutState mLayoutState;

        LayoutStateWrapper() {

        }

        LayoutStateWrapper(LayoutState layoutState) {
            this.mLayoutState = layoutState;
        }


        public int getOffset() {
            return mLayoutState.mOffset;
        }

        public int getCurrentPosition() {
            return mLayoutState.mCurrentPosition;
        }

        public boolean hasScrapList() {
            return mLayoutState.mScrapList != null;
        }

        /**
         * We may not want to recycle children in some cases (e.g. layout)
         */
        public boolean isRecycle() {
            return mLayoutState.mRecycle;
        }

        /**
         * Number of pixels that we should fill, in the layout direction.
         */
        public int getAvailable() {
            return mLayoutState.mAvailable;
        }


        /**
         * Defines the direction in which the data adapter is traversed.
         * Should be {@link #ITEM_DIRECTION_HEAD} or {@link #ITEM_DIRECTION_TAIL}
         */
        public int getItemDirection() {
            return mLayoutState.mItemDirection;
        }

        /**
         * Defines the direction in which the layout is filled.
         * Should be {@link #LAYOUT_START} or {@link #LAYOUT_END}
         */
        public int getLayoutDirection() {
            return mLayoutState.mLayoutDirection;
        }

        /**
         * Used when LayoutState is constructed in a scrolling state.
         * It should be set the amount of scrolling we can make without creating a new view.
         * Settings this is required for efficient view recycling.
         */
        public int getScrollingOffset() {
            return mLayoutState.mScrollingOffset;
        }

        /**
         * Used if you want to pre-layout items that are not yet visible.
         * The difference with {@link #getAvailable()} is that, when recycling, distance laid out for
         * {@link #getExtra()} is not considered to avoid recycling visible children.
         */
        public int getExtra() {
            return mLayoutState.mExtra;
        }

        /**
         * Equal to {@link RecyclerView.State#isPreLayout()}. When consuming scrap, if this value
         * is set to true, we skip removed views since they should not be laid out in post layout
         * step.
         */
        public boolean isPreLayout() {
            return mLayoutState.mIsPreLayout;
        }


        public boolean hasMore(RecyclerView.State state) {
            return mLayoutState.hasMore(state);
        }

        public View next(RecyclerView.Recycler recycler) {
            return mLayoutState.next(recycler);
        }

        public View retrieve(RecyclerView.Recycler recycler, int position) {
            int originPosition = mLayoutState.mCurrentPosition;
            mLayoutState.mCurrentPosition = position;
            View view = next(recycler);
            mLayoutState.mCurrentPosition = originPosition;
            return view;
        }
    }


    private static class LayoutViewHolder extends RecyclerView.ViewHolder {

        public LayoutViewHolder(View itemView) {
            super(itemView);
        }

    }


    @Override
    public View generateLayoutView() {
        // TODO: reuse LayoutViews
        LayoutView layoutView = new LayoutView(mRecyclerView.getContext());
        LayoutParams params = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        attachViewHolder(params, new LayoutViewHolder(layoutView));

        layoutView.setLayoutParams(params);
        return layoutView;
    }


    @Override
    public void addChildView(LayoutStateWrapper layoutState, View view) {
        addChildView(layoutState, view, layoutState.getItemDirection() == LayoutStateWrapper.ITEM_DIRECTION_TAIL ? -1 : 0);
    }


    @Override
    public void addChildView(LayoutStateWrapper layoutState, View view, int position) {
        if (!layoutState.hasScrapList()) {
            // can not find in scrapList
            addView(view, position);
        } else {
            addDisappearingView(view, position);
        }
    }

    @Override
    public void attachChildView(View view, boolean head) {
        attachView(view, head ? 0 : -1);
    }

    @Override
    public void detachChildView(View view) {
        detachView(view);
    }

    @Override
    public void addOffFlowView(View view, boolean head) {
        addHiddenView(view, head);
    }

    @Override
    public View findHiddenViewByPosition(int position) {
        return findHiddenView(position);
    }

    @Override
    public void removeDetachedView(View view) {
        super.removeDetachedView(view);
    }

    @Override
    public void removeChildView(View child) {
        removeView(child);
    }

    @Override
    public OrientationHelper getMainOrientationHelper() {
        return mOrientationHelper;
    }

    @Override
    public OrientationHelper getSecondaryOrientationHelper() {
        return mSecondaryOrientationHelper;
    }

    @Override
    public void measureChild(View child, int widthSpec, int heightSpec) {
        measureChildWithDecorationsAndMargin(child, widthSpec, heightSpec);
    }

    @Override
    public int getChildMeasureSpec(int parentSize, int size, boolean canScroll) {
        return getChildMeasureSpec(parentSize, 0, size, canScroll);
    }

    @Override
    public void layoutChild(View child, int left, int top, int right, int bottom) {
        layoutDecorated(child, left, top, right, bottom);
    }

    @Override
    protected void recycleChildren(RecyclerView.Recycler recycler, int startIndex, int endIndex) {
        if (startIndex == endIndex) {
            return;
        }

        if (DEBUG) {
            Log.d(TAG, "Recycling " + Math.abs(startIndex - endIndex) + " items");
        }

        if (endIndex > startIndex) {

            View endView = getChildAt(endIndex - 1);
            View startView = getChildAt(startIndex);

            int startPos = getPosition(startView);
            int endPos = getPosition(endView);

            int idx = startIndex;

            for (int i = startIndex; i < endIndex; i++) {
                View v = getChildAt(idx);
                int pos = getPosition(v);
                if (pos != RecyclerView.NO_POSITION) {
                    LayoutHelper layoutHelper = mHelperFinder.getLayoutHelper(pos);
                    if (layoutHelper == null || layoutHelper.isRecyclable(pos, startPos, endPos, this, true)) {
                        removeAndRecycleViewAt(idx, recycler);
                    } else {
                        idx++;
                    }
                } else
                    removeAndRecycleViewAt(idx, recycler);
            }
        } else {

            View endView = getChildAt(startIndex);
            View startView = getChildAt(endIndex + 1);

            int startPos = getPosition(startView);
            int endPos = getPosition(endView);

            for (int i = startIndex; i > endIndex; i--) {
                View v = getChildAt(i);
                int pos = getPosition(v);
                if (pos != RecyclerView.NO_POSITION) {
                    LayoutHelper layoutHelper = mHelperFinder.getLayoutHelper(pos);
                    if (layoutHelper == null || layoutHelper.isRecyclable(pos, startPos, endPos, this, false)) {
                        removeAndRecycleViewAt(i, recycler);
                    }
                } else
                    removeAndRecycleViewAt(i, recycler);
            }
        }
    }

    @Override
    public int getContentWidth() {
        return super.getWidth();
    }

    @Override
    public int getContentHeight() {
        return super.getHeight();
    }

    @Override
    public boolean isDoLayoutRTL() {
        return isLayoutRTL();
    }

    private Rect mDecorInsets = new Rect();

    private void measureChildWithDecorationsAndMargin(View child, int widthSpec, int heightSpec) {
        calculateItemDecorationsForChild(child, mDecorInsets);
        RecyclerView.LayoutParams lp = (RecyclerView.LayoutParams) child.getLayoutParams();
        widthSpec = updateSpecWithExtra(widthSpec, lp.leftMargin + mDecorInsets.left,
                lp.rightMargin + mDecorInsets.right);
        heightSpec = updateSpecWithExtra(heightSpec, lp.topMargin + mDecorInsets.top,
                lp.bottomMargin + mDecorInsets.bottom);
        child.measure(widthSpec, heightSpec);
    }

    private int updateSpecWithExtra(int spec, int startInset, int endInset) {
        if (startInset == 0 && endInset == 0) {
            return spec;
        }
        final int mode = View.MeasureSpec.getMode(spec);
        if (mode == View.MeasureSpec.AT_MOST || mode == View.MeasureSpec.EXACTLY) {
            return View.MeasureSpec.makeMeasureSpec(
                    View.MeasureSpec.getSize(spec) - startInset - endInset, mode);
        }
        return spec;
    }

}
