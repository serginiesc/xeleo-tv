package com.xeleo.tv;

import android.content.Context;
import android.content.Intent;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;

import java.util.ArrayList;
import java.util.List;

/**
 * The TV's own inputs (HDMI 1, AV…), for TVs with Android inside, such as a TCL: with XELEO as the home screen there
 * has to be a way to reach them. A streaming box like the onn has none, so nothing about them shows there.
 */
final class TvInputs {

    /** A name and what opens it; a null intent is a sample (ControlReceiver INPUTS_PREVIEW), which opens nothing. */
    static final class Input {
        final String label;
        final Intent open;

        Input(String label, Intent open) {
            this.label = label;
            this.open = open;
        }
    }

    /** Shows a sample list on a box without inputs, to see how it looks. */
    static volatile boolean preview;

    private TvInputs() {}

    /** The inputs a device can be plugged into; empty on a box without any. */
    static List<Input> list(Context c) {
        List<Input> out = new ArrayList<>();
        if (preview) {
            out.add(new Input("HDMI 1", null));
            out.add(new Input("HDMI 2", null));
            out.add(new Input("AV", null));
            return out;
        }
        try {
            TvInputManager tv = c.getSystemService(TvInputManager.class);
            if (tv == null) return out;
            for (TvInputInfo i : tv.getTvInputList()) {
                if (!i.isPassthroughInput() || i.isHidden(c)) continue;
                CharSequence custom = i.loadCustomLabel(c);
                CharSequence label = custom != null && custom.length() > 0 ? custom : i.loadLabel(c);
                out.add(new Input(String.valueOf(label),
                        new Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(i.getId()))));
            }
        } catch (RuntimeException ignored) {
            // A TV that doesn't answer: as if it had none.
        }
        return out;
    }
}
