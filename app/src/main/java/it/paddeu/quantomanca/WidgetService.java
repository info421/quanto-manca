package it.paddeu.quantomanca;

import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.provider.CalendarContract;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import java.util.ArrayList;
import java.util.List;

/** Fornisce le righe della lista al widget. */
public class WidgetService extends RemoteViewsService {

    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        return new Factory(getApplicationContext());
    }

    static class Factory implements RemoteViewsFactory {
        private final Context c;
        private List<Ev> items = new ArrayList<>();
        private long now = System.currentTimeMillis();

        Factory(Context c) { this.c = c; }

        @Override public void onCreate() {}

        @Override
        public void onDataSetChanged() {
            long token = Binder.clearCallingIdentity();
            try {
                items = EventRepo.load(c);
                now = System.currentTimeMillis();
            } finally {
                Binder.restoreCallingIdentity(token);
            }
        }

        @Override public void onDestroy() { items.clear(); }

        @Override public int getCount() { return items.size(); }

        @Override
        public RemoteViews getViewAt(int pos) {
            RemoteViews rv = new RemoteViews(c.getPackageName(), R.layout.widget_item);
            if (pos < 0 || pos >= items.size()) return rv;
            Ev e = items.get(pos);
            int col = Fmt.color(e, now);

            rv.setTextViewText(R.id.title, e.title);
            rv.setTextViewText(R.id.meta, Fmt.when(e));
            rv.setTextViewText(R.id.big, (Fmt.live(e, now) ? "● " : "") + Fmt.big(e, now));
            rv.setTextColor(R.id.big, col);
            rv.setTextViewText(R.id.small, Fmt.small(e, now));
            rv.setInt(R.id.bar, "setBackgroundColor", col);

            Intent fill = new Intent();
            fill.setData(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, e.eventId));
            fill.putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, e.rawBegin);
            fill.putExtra(CalendarContract.EXTRA_EVENT_END_TIME, e.rawEnd);
            rv.setOnClickFillInIntent(R.id.item_root, fill);
            return rv;
        }

        @Override public RemoteViews getLoadingView() { return null; }
        @Override public int getViewTypeCount() { return 1; }
        @Override public long getItemId(int pos) { return pos; }
        @Override public boolean hasStableIds() { return false; }
    }
}
