package com.example.personallibrary;

import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.RatingBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class MainActivity extends AppCompatActivity {

    private AppDatabase db;
    private BookAdapter adapter;
    private View layoutEmpty;
    private TextView tvNoResults;
    private TextView tvLibraryStats;
    private ExtendedFloatingActionButton fab;

    // Search / filter state
    private final List<Book> allBooks = new ArrayList<>();
    private String query = "";
    private String statusFilter = null; // null = All

    // Gallery cover picking
    private ActivityResultLauncher<PickVisualMediaRequest> pickCover;
    private String pendingCoverPath = null;
    private TextView currentCoverHint = null;

    // Backup (export / import)
    private ActivityResultLauncher<String> exportLauncher;
    private ActivityResultLauncher<String[]> importLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Keep content clear of the status bar / camera cutout / navigation bar
        View root = findViewById(R.id.main);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, windowInsets) -> {
            Insets bars = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return windowInsets;
        });

        db = AppDatabase.getInstance(this);

        RecyclerView rvBooks = findViewById(R.id.rvBooks);
        layoutEmpty = findViewById(R.id.layoutEmpty);
        tvNoResults = findViewById(R.id.tvNoResults);
        tvLibraryStats = findViewById(R.id.tvLibraryStats);
        fab = findViewById(R.id.fabAddBook);
        TextInputEditText etSearch = findViewById(R.id.etSearch);
        ChipGroup chipGroupFilter = findViewById(R.id.chipGroupFilter);

        // Gallery picker (must be registered before the activity starts)
        pickCover = registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
            if (uri != null) copyCoverToAppStorage(uri);
        });

        // Backup: save / load the whole library as one .zip file
        exportLauncher = registerForActivityResult(
                new ActivityResultContracts.CreateDocument("application/zip"), uri -> {
                    if (uri != null) exportLibrary(uri);
                });
        importLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(), uri -> {
                    if (uri != null) importLibrary(uri);
                });
        findViewById(R.id.btnMenu).setOnClickListener(this::showMoreMenu);

        adapter = new BookAdapter();
        adapter.setOnBookClickListener(this::showBookDialog); // tap a card to edit/delete
        rvBooks.setAdapter(adapter);

        // Swipe a card left or right to delete, with Undo
        new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(0,
                ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView,
                                  @NonNull RecyclerView.ViewHolder viewHolder,
                                  @NonNull RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getBindingAdapterPosition();
                if (position == RecyclerView.NO_POSITION) return;
                deleteWithUndo(adapter.getBookAt(position));
            }
        }).attachToRecyclerView(rvBooks);

        // Search box
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                query = s.toString();
                applyFilters();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        // Status filter chips
        chipGroupFilter.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int id = checkedIds.get(0);
            if (id == R.id.chipFilterWant) statusFilter = Book.STATUS_WANT_TO_READ;
            else if (id == R.id.chipFilterReading) statusFilter = Book.STATUS_READING;
            else if (id == R.id.chipFilterFinished) statusFilter = Book.STATUS_FINISHED;
            else statusFilter = null;
            applyFilters();
        });

        db.bookDao().getAllBooks().observe(this, this::onBooksChanged);

        fab.setOnClickListener(v -> showBookDialog(null));
    }

    private void onBooksChanged(List<Book> books) {
        allBooks.clear();
        allBooks.addAll(books);

        int total = books.size();
        int finished = 0;
        for (Book b : books) {
            if (Book.STATUS_FINISHED.equals(b.getStatus())) finished++;
        }
        String count = getResources().getQuantityString(R.plurals.book_count, total, total);
        tvLibraryStats.setText(getString(R.string.library_stats, count, finished));

        applyFilters();
    }

    private void applyFilters() {
        String q = query.trim().toLowerCase(Locale.getDefault());
        List<Book> filtered = new ArrayList<>();

        for (Book b : allBooks) {
            if (statusFilter != null && !statusFilter.equals(b.getStatus())) continue;
            if (!q.isEmpty()
                    && !b.getTitle().toLowerCase(Locale.getDefault()).contains(q)
                    && !b.getAuthor().toLowerCase(Locale.getDefault()).contains(q)) {
                continue;
            }
            filtered.add(b);
        }

        adapter.setBooks(filtered);

        boolean libraryEmpty = allBooks.isEmpty();
        layoutEmpty.setVisibility(libraryEmpty ? View.VISIBLE : View.GONE);
        tvNoResults.setVisibility(!libraryEmpty && filtered.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void deleteWithUndo(Book book) {
        AppDatabase.databaseWriteExecutor.execute(() -> db.bookDao().delete(book));

        Snackbar snackbar = Snackbar.make(findViewById(R.id.main),
                getString(R.string.book_deleted, book.getTitle()), Snackbar.LENGTH_LONG);
        snackbar.setAction(R.string.undo, v ->
                AppDatabase.databaseWriteExecutor.execute(() -> db.bookDao().insert(book)));
        snackbar.setAnchorView(fab);
        snackbar.setBackgroundTint(Color.parseColor("#1A1A1F"));
        snackbar.setTextColor(Color.parseColor("#EDE8DC"));
        snackbar.setActionTextColor(Color.parseColor("#D4AF6A"));
        snackbar.show();
    }

    /** Copies the picked gallery image into the app's private storage. */
    private void copyCoverToAppStorage(Uri uri) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            File dir = new File(getFilesDir(), "covers");
            if (!dir.exists() && !dir.mkdirs()) {
                runOnUiThread(() -> Toast.makeText(this, R.string.cover_error,
                        Toast.LENGTH_SHORT).show());
                return;
            }
            File out = new File(dir, "cover_" + System.currentTimeMillis() + ".jpg");

            try (InputStream in = getContentResolver().openInputStream(uri);
                 OutputStream os = new FileOutputStream(out)) {
                if (in == null) throw new IOException("No input stream");
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) > 0) {
                    os.write(buffer, 0, read);
                }
                runOnUiThread(() -> {
                    pendingCoverPath = out.getAbsolutePath();
                    if (currentCoverHint != null) {
                        currentCoverHint.setText(R.string.cover_hint_chosen);
                    }
                });
            } catch (IOException e) {
                runOnUiThread(() -> Toast.makeText(this, R.string.cover_error,
                        Toast.LENGTH_SHORT).show());
            }
        });
    }

    // ---------------------------------------------------------------------
    // Backup: export / import
    // ---------------------------------------------------------------------

    private void showMoreMenu(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().add(0, 1, 0, R.string.export_library);
        popup.getMenu().add(0, 2, 1, R.string.import_library);
        popup.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) {
                if (allBooks.isEmpty()) {
                    Toast.makeText(this, R.string.export_empty, Toast.LENGTH_SHORT).show();
                } else {
                    exportLauncher.launch("personal-library-backup.zip");
                }
            } else if (item.getItemId() == 2) {
                importLauncher.launch(new String[]{"*/*"});
            }
            return true;
        });
        popup.show();
    }

    /** Writes every book (and its gallery cover image) into one .zip file. */
    private void exportLibrary(Uri uri) {
        final List<Book> books = new ArrayList<>(allBooks);

        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                OutputStream raw = getContentResolver().openOutputStream(uri);
                if (raw == null) throw new IOException("Cannot open backup file");

                try (ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(raw))) {
                    JSONArray array = new JSONArray();
                    Set<String> written = new HashSet<>();

                    for (Book b : books) {
                        JSONObject o = new JSONObject();
                        o.put("title", b.getTitle());
                        o.put("author", b.getAuthor());
                        o.put("format", b.getFormat());
                        o.put("status", b.getStatus());
                        o.put("favoriteQuote", b.getFavoriteQuote() == null
                                ? JSONObject.NULL : b.getFavoriteQuote());
                        o.put("rating", b.getRating());
                        o.put("totalPages", b.getTotalPages());
                        o.put("currentPage", b.getCurrentPage());

                        String cover = b.getCoverUrl();
                        if (cover != null && cover.startsWith("/")) {
                            // Picture the user chose from the gallery: store the image itself
                            File f = new File(cover);
                            if (f.exists()) {
                                String entryName = "covers/" + f.getName();
                                if (written.add(entryName)) {
                                    zip.putNextEntry(new ZipEntry(entryName));
                                    try (InputStream in = new FileInputStream(f)) {
                                        copy(in, zip);
                                    }
                                    zip.closeEntry();
                                }
                                o.put("coverFile", entryName);
                            }
                        } else if (cover != null) {
                            o.put("coverUrl", cover); // online cover: just remember the link
                        }
                        array.put(o);
                    }

                    JSONObject root = new JSONObject();
                    root.put("app", "PersonalLibrary");
                    root.put("version", 1);
                    root.put("books", array);

                    zip.putNextEntry(new ZipEntry("books.json"));
                    zip.write(root.toString(2).getBytes(StandardCharsets.UTF_8));
                    zip.closeEntry();
                }

                runOnUiThread(() -> Toast.makeText(this, R.string.export_done,
                        Toast.LENGTH_LONG).show());
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, R.string.export_error,
                        Toast.LENGTH_LONG).show());
            }
        });
    }

    /** Adds the books from a backup .zip. Books already on the shelf are skipped. */
    private void importLibrary(Uri uri) {
        final Set<String> existingKeys = new HashSet<>();
        for (Book b : allBooks) existingKeys.add(bookKey(b.getTitle(), b.getAuthor()));

        AppDatabase.databaseWriteExecutor.execute(() -> {
            File coverDir = new File(getFilesDir(), "covers");
            Map<String, String> extracted = new HashMap<>();
            byte[] jsonBytes = null;

            try {
                InputStream raw = getContentResolver().openInputStream(uri);
                if (raw == null) throw new IOException("Cannot open file");

                try (ZipInputStream zip = new ZipInputStream(new BufferedInputStream(raw))) {
                    ZipEntry entry;
                    while ((entry = zip.getNextEntry()) != null) {
                        if (entry.isDirectory()) continue;
                        String name = entry.getName();

                        if (name.equals("books.json")) {
                            jsonBytes = readAll(zip, 20 * 1024 * 1024);
                        } else if (name.startsWith("covers/")) {
                            if (!coverDir.exists() && !coverDir.mkdirs()) continue;
                            File out = new File(coverDir,
                                    "imp_" + System.nanoTime() + "_" + new File(name).getName());
                            try (OutputStream os = new FileOutputStream(out)) {
                                copy(zip, os);
                            }
                            extracted.put(name, out.getAbsolutePath());
                        }
                    }
                }
                if (jsonBytes == null) throw new IOException("books.json missing");

                JSONArray array = new JSONObject(new String(jsonBytes, StandardCharsets.UTF_8))
                        .getJSONArray("books");
                BookDao dao = db.bookDao();
                Set<String> usedCovers = new HashSet<>();
                int imported = 0;
                int skipped = 0;

                for (int i = 0; i < array.length(); i++) {
                    JSONObject o = array.getJSONObject(i);
                    String title = o.optString("title", "").trim();
                    String author = o.optString("author", "").trim();
                    if (title.isEmpty() || author.isEmpty()) continue;

                    if (!existingKeys.add(bookKey(title, author))) {
                        skipped++;
                        continue;
                    }

                    String quote = o.isNull("favoriteQuote") ? null : o.getString("favoriteQuote");
                    if (quote != null && quote.trim().isEmpty()) quote = null;

                    Book book = new Book(title, author,
                            validFormat(o.optString("format", "")),
                            validStatus(o.optString("status", "")),
                            quote,
                            Math.max(0, Math.min(5, o.optInt("rating", 0))));
                    book.setTotalPages(Math.max(0, o.optInt("totalPages", 0)));
                    book.setCurrentPage(Math.max(0, o.optInt("currentPage", 0)));

                    String coverFile = o.optString("coverFile", "");
                    String coverUrl = o.optString("coverUrl", "");
                    if (!coverFile.isEmpty() && extracted.containsKey(coverFile)) {
                        book.setCoverUrl(extracted.get(coverFile));
                        usedCovers.add(coverFile);
                    } else if (coverUrl.startsWith("http")) {
                        book.setCoverUrl(coverUrl);
                    }

                    dao.insert(book);
                    imported++;
                }

                // Remove extracted pictures that no imported book ended up using
                for (Map.Entry<String, String> item : extracted.entrySet()) {
                    if (!usedCovers.contains(item.getKey())) {
                        //noinspection ResultOfMethodCallIgnored
                        new File(item.getValue()).delete();
                    }
                }

                final int doneImported = imported;
                final int doneSkipped = skipped;
                runOnUiThread(() -> Toast.makeText(this,
                        getString(R.string.import_done, doneImported, doneSkipped),
                        Toast.LENGTH_LONG).show());
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, R.string.import_error,
                        Toast.LENGTH_LONG).show());
            }
        });
    }

    private static String bookKey(String title, String author) {
        return title.trim().toLowerCase(Locale.ROOT) + "|" + author.trim().toLowerCase(Locale.ROOT);
    }

    private static String validFormat(String value) {
        if (Book.FORMAT_EREADER.equals(value) || Book.FORMAT_AUDIOBOOK.equals(value)) return value;
        return Book.FORMAT_PHYSICAL;
    }

    private static String validStatus(String value) {
        if (Book.STATUS_READING.equals(value) || Book.STATUS_FINISHED.equals(value)) return value;
        return Book.STATUS_WANT_TO_READ;
    }

    private static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) > 0) {
            out.write(buffer, 0, read);
        }
    }

    private static byte[] readAll(InputStream in, int maxBytes) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        int total = 0;
        while ((read = in.read(buffer)) > 0) {
            total += read;
            if (total > maxBytes) throw new IOException("File too large");
            bos.write(buffer, 0, read);
        }
        return bos.toByteArray();
    }

    /** existing == null -> add a new book; otherwise edit/delete that book. */
    private void showBookDialog(@Nullable Book existing) {
        final boolean isEdit = existing != null;
        pendingCoverPath = null;

        View view = LayoutInflater.from(this).inflate(R.layout.dialog_add_book, null);

        TextView tvDialogTitle = view.findViewById(R.id.tvDialogTitle);
        TextInputLayout tilTitle = view.findViewById(R.id.tilTitle);
        TextInputLayout tilAuthor = view.findViewById(R.id.tilAuthor);
        TextInputLayout tilCurrentPage = view.findViewById(R.id.tilCurrentPage);
        TextInputEditText etTitle = view.findViewById(R.id.etTitle);
        TextInputEditText etAuthor = view.findViewById(R.id.etAuthor);
        TextInputEditText etQuote = view.findViewById(R.id.etQuote);
        TextInputEditText etCurrentPage = view.findViewById(R.id.etCurrentPage);
        TextInputEditText etTotalPages = view.findViewById(R.id.etTotalPages);
        ChipGroup chipGroupFormat = view.findViewById(R.id.chipGroupFormat);
        ChipGroup chipGroupStatus = view.findViewById(R.id.chipGroupStatus);
        RatingBar ratingBar = view.findViewById(R.id.ratingBar);
        View layoutProgress = view.findViewById(R.id.layoutProgress);
        View btnPickCover = view.findViewById(R.id.btnPickCover);

        currentCoverHint = view.findViewById(R.id.tvCoverHint);

        btnPickCover.setOnClickListener(v -> pickCover.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                .build()));

        // Page fields only make sense while the book is being read
        chipGroupStatus.setOnCheckedStateChangeListener((group, ids) ->
                layoutProgress.setVisibility(
                        ids.contains(R.id.chipReading) ? View.VISIBLE : View.GONE));

        if (isEdit) {
            tvDialogTitle.setText(R.string.edit_book);
            etTitle.setText(existing.getTitle());
            etAuthor.setText(existing.getAuthor());
            if (existing.getFavoriteQuote() != null) {
                etQuote.setText(existing.getFavoriteQuote());
            }
            ratingBar.setRating(existing.getRating());
            if (existing.getCurrentPage() > 0) {
                etCurrentPage.setText(String.valueOf(existing.getCurrentPage()));
            }
            if (existing.getTotalPages() > 0) {
                etTotalPages.setText(String.valueOf(existing.getTotalPages()));
            }

            if (Book.FORMAT_EREADER.equals(existing.getFormat())) {
                chipGroupFormat.check(R.id.chipEreader);
            } else if (Book.FORMAT_AUDIOBOOK.equals(existing.getFormat())) {
                chipGroupFormat.check(R.id.chipAudiobook);
            } else {
                chipGroupFormat.check(R.id.chipPhysical);
            }

            if (Book.STATUS_READING.equals(existing.getStatus())) {
                chipGroupStatus.check(R.id.chipReading);
            } else if (Book.STATUS_FINISHED.equals(existing.getStatus())) {
                chipGroupStatus.check(R.id.chipFinished);
            } else {
                chipGroupStatus.check(R.id.chipWant);
            }
        }

        layoutProgress.setVisibility(
                chipGroupStatus.getCheckedChipId() == R.id.chipReading ? View.VISIBLE : View.GONE);

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                .setView(view)
                .setNegativeButton("Cancel", null)
                .setPositiveButton(isEdit ? "Update" : "Save", null);
        if (isEdit) builder.setNeutralButton("Delete", null);

        AlertDialog dialog = builder.create();
        dialog.setOnDismissListener(d -> currentCoverHint = null);
        dialog.show();

        if (isEdit) {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
                    .setOnClickListener(v -> confirmDelete(existing, dialog));
        }

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            final String title = String.valueOf(etTitle.getText()).trim();
            final String author = String.valueOf(etAuthor.getText()).trim();
            final int totalPages = parseIntSafe(etTotalPages.getText());
            final int currentPage = parseIntSafe(etCurrentPage.getText());

            tilTitle.setError(title.isEmpty() ? "Enter a title" : null);
            tilAuthor.setError(author.isEmpty() ? "Enter an author" : null);
            boolean pagesInvalid = totalPages > 0 && currentPage > totalPages;
            tilCurrentPage.setError(pagesInvalid ? getString(R.string.pages_error) : null);
            if (title.isEmpty() || author.isEmpty() || pagesInvalid) return;

            final String format = formatFromChip(chipGroupFormat.getCheckedChipId());
            final String status = statusFromChip(chipGroupStatus.getCheckedChipId());
            final String quoteText = String.valueOf(etQuote.getText()).trim();
            final String quote = quoteText.isEmpty() ? null : quoteText;
            final int rating = Math.round(ratingBar.getRating());
            final String chosenCover = pendingCoverPath;

            final Book book;
            final boolean needsCover;

            if (isEdit) {
                book = existing;
                boolean titleOrAuthorChanged = !book.getTitle().equals(title)
                        || !book.getAuthor().equals(author);
                String oldCover = book.getCoverUrl();

                book.setTitle(title);
                book.setAuthor(author);
                book.setFormat(format);
                book.setStatus(status);
                book.setFavoriteQuote(quote);
                book.setRating(rating);
                book.setTotalPages(totalPages);
                book.setCurrentPage(currentPage);

                if (chosenCover != null) {
                    book.setCoverUrl(chosenCover);
                } else if (titleOrAuthorChanged && oldCover != null
                        && oldCover.startsWith("http")) {
                    book.setCoverUrl(null); // auto-found cover may no longer match
                }
                needsCover = book.getCoverUrl() == null;
            } else {
                book = new Book(title, author, format, status, quote, rating);
                book.setTotalPages(totalPages);
                book.setCurrentPage(currentPage);
                if (chosenCover != null) book.setCoverUrl(chosenCover);
                needsCover = chosenCover == null;
            }

            AppDatabase.databaseWriteExecutor.execute(() -> {
                BookDao dao = db.bookDao();
                if (isEdit) {
                    dao.update(book);
                } else {
                    book.setId((int) dao.insert(book));
                }

                // No cover picked: look one up online, then save it onto the book
                if (needsCover) {
                    String url = CoverFetcher.fetchCoverUrl(title, author);
                    if (url != null) {
                        book.setCoverUrl(url);
                        dao.update(book);
                    }
                }
            });

            dialog.dismiss();
        });
    }

    private void confirmDelete(Book book, AlertDialog parentDialog) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Delete this book?")
                .setMessage("“" + book.getTitle() + "” will be removed from your shelf.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (d, which) -> {
                    AppDatabase.databaseWriteExecutor.execute(() -> db.bookDao().delete(book));
                    parentDialog.dismiss();
                })
                .show();
    }

    private int parseIntSafe(@Nullable CharSequence s) {
        try {
            return Integer.parseInt(String.valueOf(s).trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String formatFromChip(int chipId) {
        if (chipId == R.id.chipEreader) return Book.FORMAT_EREADER;
        if (chipId == R.id.chipAudiobook) return Book.FORMAT_AUDIOBOOK;
        return Book.FORMAT_PHYSICAL;
    }

    private String statusFromChip(int chipId) {
        if (chipId == R.id.chipReading) return Book.STATUS_READING;
        if (chipId == R.id.chipFinished) return Book.STATUS_FINISHED;
        return Book.STATUS_WANT_TO_READ;
    }
}