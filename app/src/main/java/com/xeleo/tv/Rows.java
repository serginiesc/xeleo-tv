package com.xeleo.tv;

import android.view.View;
import android.view.ViewGroup;

/** Focus helpers for the rows of cards and chips. */
final class Rows {

    private Rows() {}

    /**
     * ◀ on the first item and ▶ on the last stay put, instead of jumping to whatever row happens to lie in that
     * direction (the remote should never leave a row sideways).
     */
    static void fence(ViewGroup row) {
        View first = null, last = null;
        for (int i = 0; i < row.getChildCount(); i++) {
            View v = row.getChildAt(i);
            if (!v.isFocusable()) continue;
            if (v.getId() == View.NO_ID) v.setId(View.generateViewId());
            v.setNextFocusLeftId(View.NO_ID);
            v.setNextFocusRightId(View.NO_ID);
            if (first == null) first = v;
            last = v;
        }
        if (first == null) return;
        first.setNextFocusLeftId(first.getId());
        last.setNextFocusRightId(last.getId());
    }
}
