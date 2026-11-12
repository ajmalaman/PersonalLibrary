package com.example.personallibrary;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.ArrayList;
import java.util.List;

public class BookAdapter extends RecyclerView.Adapter<BookAdapter.BookViewHolder> {

    public interface OnBookClickListener {
        void onBookClick(Book book);
    }

    private static final int COLOR_WANT = Color.parseColor("#6B6B73");
    private static final int COLOR_READING = Color.parseColor("#D4AF6A");
    private static final int COLOR_FINISHED = Color.parseColor("#5FB88A");
    private static final int COLOR_STAR_EMPTY = Color.parseColor("#3A3A42");

    private final List<Book> books = new ArrayList<>();
    private OnBookClickListener listener;

    public void setOnBookClickListener(OnBookClickListener listener) {
        this.listener = listener;
    }

    public void setBooks(List<Book> newBooks) {
        books.clear();
        if (newBooks != null) books.addAll(newBooks);
        notifyDataSetChanged();
    }

    public Book getBookAt(int position) {
        return books.get(position);
    }

    @NonNull
    @Override
    public BookViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_book, parent, false);
        return new BookViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull BookViewHolder h, int position) {
        Book b = books.get(position);

        h.title.setText(b.getTitle());
        h.author.setText(b.getAuthor());
        h.format.setText(b.getFormat());
        h.status.setText(b.getStatus());

        int color = statusColor(b.getStatus());
        h.accent.setBackgroundColor(color);
        h.statusDot.setBackgroundTintList(ColorStateList.valueOf(color));

        String quote = b.getFavoriteQuote() == null ? "" : cleanQuote(b.getFavoriteQuote());
        if (quote.isEmpty()) {
            h.quote.setVisibility(View.GONE);
            h.quoteDivider.setVisibility(View.GONE);
        } else {
            h.quote.setVisibility(View.VISIBLE);
            h.quoteDivider.setVisibility(View.VISIBLE);
            h.quote.setText(h.itemView.getContext().getString(R.string.quote_format, quote));
        }

        int rating = Math.max(0, Math.min(5, b.getRating()));
        if (rating == 0) {
            h.rating.setVisibility(View.GONE);
        } else {
            h.rating.setVisibility(View.VISIBLE);
            SpannableString stars = new SpannableString("★★★★★");
            if (rating < 5) {
                stars.setSpan(new ForegroundColorSpan(COLOR_STAR_EMPTY), rating, 5,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            h.rating.setText(stars);
        }

        // Reading progress (only while Reading and a page count is known)
        int totalPages = b.getTotalPages();
        if (Book.STATUS_READING.equals(b.getStatus()) && totalPages > 0) {
            int current = Math.max(0, Math.min(b.getCurrentPage(), totalPages));
            int percent = Math.round(current * 100f / totalPages);
            h.progressBar.setVisibility(View.VISIBLE);
            h.progressText.setVisibility(View.VISIBLE);
            h.progressBar.setProgress(percent);
            h.progressText.setText(h.itemView.getContext()
                    .getString(R.string.pages_progress, current, totalPages, percent));
        } else {
            h.progressBar.setVisibility(View.GONE);
            h.progressText.setVisibility(View.GONE);
        }

        // Cover image (card is hidden when the book has no cover)
        String coverUrl = b.getCoverUrl();
        if (coverUrl == null || coverUrl.isEmpty()) {
            Glide.with(h.cover).clear(h.cover);
            h.cardCover.setVisibility(View.GONE);
        } else {
            h.cardCover.setVisibility(View.VISIBLE);
            Glide.with(h.cover).load(coverUrl).centerCrop().into(h.cover);
        }

        h.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onBookClick(b);
        });
    }

    // Removes quote marks the user typed, so we can add clean typographic ones
    private String cleanQuote(String q) {
        String s = q.trim();
        while (!s.isEmpty() && isQuoteChar(s.charAt(0))) s = s.substring(1).trim();
        while (!s.isEmpty() && isQuoteChar(s.charAt(s.length() - 1))) {
            s = s.substring(0, s.length() - 1).trim();
        }
        return s;
    }

    private boolean isQuoteChar(char c) {
        return c == '"' || c == '“' || c == '”';
    }

    private int statusColor(String status) {
        if (Book.STATUS_READING.equals(status)) return COLOR_READING;
        if (Book.STATUS_FINISHED.equals(status)) return COLOR_FINISHED;
        return COLOR_WANT;
    }

    @Override
    public int getItemCount() {
        return books.size();
    }

    public static class BookViewHolder extends RecyclerView.ViewHolder {
        final TextView title, author, format, status, quote, rating;
        final View accent, statusDot, quoteDivider, cardCover;
        final ImageView cover;
        final TextView progressText;
        final LinearProgressIndicator progressBar;

        BookViewHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.tvTitle);
            author = itemView.findViewById(R.id.tvAuthor);
            format = itemView.findViewById(R.id.tvFormat);
            status = itemView.findViewById(R.id.tvStatus);
            quote = itemView.findViewById(R.id.tvQuote);
            rating = itemView.findViewById(R.id.tvRating);
            accent = itemView.findViewById(R.id.viewAccent);
            statusDot = itemView.findViewById(R.id.viewStatusDot);
            quoteDivider = itemView.findViewById(R.id.viewQuoteDivider);
            cardCover = itemView.findViewById(R.id.cardCover);
            cover = itemView.findViewById(R.id.ivCover);
            progressText = itemView.findViewById(R.id.tvProgress);
            progressBar = itemView.findViewById(R.id.progressReading);
        }
    }
}