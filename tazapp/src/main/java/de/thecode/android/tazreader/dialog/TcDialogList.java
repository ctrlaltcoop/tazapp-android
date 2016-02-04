package de.thecode.android.tazreader.dialog;

import android.app.Activity;
import android.content.DialogInterface;
import android.os.Bundle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by Mate on 18.03.2015.
 */
public class TcDialogList extends TcDialog {

    private static final Logger log = LoggerFactory.getLogger(TcDialogList.class);

    static final String ARG_ARRAY_STRING_ITEMS = "list_items";

    TcDialogListListener listListener;

    DialogInterface.OnClickListener onClickListener = new DialogInterface.OnClickListener() {

        @Override
        public void onClick(DialogInterface dialog, int which) {
            String[] items = getItems();
            String value = items[which];

            log.debug("Button {} {}", which,value);
            if (listListener != null) listListener.onDialogListClick(getTag(), TcDialogList.this.getArguments(), which,value,getItems());
            else log.info(TcDialogListListener.class.getSimpleName() + " not set in Activity " + getActivity().getClass()
                                                                                                             .getSimpleName());
        }
    };


    public TcDialogList withList(String... items)
    {
        args.putStringArray(ARG_ARRAY_STRING_ITEMS, items);
        return this;
    }

    @Override
    void setDialogContent() {
        if (hasItems())
            builder.setItems(getItems(), onClickListener);
    }

    @Override
    public void onAttach(Activity activity) {
        log.debug(this.getTag());
        super.onAttach(activity);
        try {
            listListener = (TcDialogListListener) activity;
        } catch (ClassCastException e) {
            log.warn(e.getMessage());
        }
    }

    protected boolean hasItems()
    {
        return getArguments().containsKey(ARG_ARRAY_STRING_ITEMS);
    }

    protected String[] getItems()
    {
        return getArguments().getStringArray(ARG_ARRAY_STRING_ITEMS);
    }

    public interface TcDialogListListener {
        public void onDialogListClick(String tag, Bundle arguments, int which, String value, String[] items);
    }

}