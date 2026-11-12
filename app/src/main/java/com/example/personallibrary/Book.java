package com.example.personallibrary;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

@Entity(tableName = "books")
public class Book {

    // Allowed values for Format
    public static final String FORMAT_PHYSICAL = "Physical";
    public static final String FORMAT_EREADER = "E-reader";
    public static final String FORMAT_AUDIOBOOK = "Audiobook";

    // Allowed values for Reading Status
    public static final String STATUS_WANT_TO_READ = "Want to Read";
    public static final String STATUS_READING = "Reading";
    public static final String STATUS_FINISHED = "Finished";

    @PrimaryKey(autoGenerate = true)
    private int id;

    @NonNull
    private String title = "";

    @NonNull
    private String author = "";

    @NonNull
    private String format = FORMAT_PHYSICAL;

    @NonNull
    private String status = STATUS_WANT_TO_READ;

    private String favoriteQuote;

    // 1-5, or 0 if not rated yet
    private int rating;

    // URL of the cover image (null until found)
    @Nullable
    private String coverUrl;

    // Reading progress (0 = not set)
    private int totalPages;
    private int currentPage;

    // Used by Room
    public Book() {
    }

    // Convenience constructor for creating new books (id is auto-generated)
    @Ignore
    public Book(@NonNull String title, @NonNull String author, @NonNull String format,
                @NonNull String status, String favoriteQuote, int rating) {
        this.title = title;
        this.author = author;
        this.format = format;
        this.status = status;
        this.favoriteQuote = favoriteQuote;
        this.rating = rating;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    @NonNull
    public String getTitle() { return title; }
    public void setTitle(@NonNull String title) { this.title = title; }

    @NonNull
    public String getAuthor() { return author; }
    public void setAuthor(@NonNull String author) { this.author = author; }

    @NonNull
    public String getFormat() { return format; }
    public void setFormat(@NonNull String format) { this.format = format; }

    @NonNull
    public String getStatus() { return status; }
    public void setStatus(@NonNull String status) { this.status = status; }

    public String getFavoriteQuote() { return favoriteQuote; }
    public void setFavoriteQuote(String favoriteQuote) { this.favoriteQuote = favoriteQuote; }

    public int getRating() { return rating; }
    public void setRating(int rating) { this.rating = rating; }

    @Nullable
    public String getCoverUrl() { return coverUrl; }
    public void setCoverUrl(@Nullable String coverUrl) { this.coverUrl = coverUrl; }

    public int getTotalPages() { return totalPages; }
    public void setTotalPages(int totalPages) { this.totalPages = totalPages; }

    public int getCurrentPage() { return currentPage; }
    public void setCurrentPage(int currentPage) { this.currentPage = currentPage; }
}