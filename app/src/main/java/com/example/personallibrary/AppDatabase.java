package com.example.personallibrary;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Database(entities = {Book.class}, version = 3, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    public abstract BookDao bookDao();

    // Version 1 -> 2: adds the cover image URL column, keeping all existing books
    static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE books ADD COLUMN coverUrl TEXT");
        }
    };

    // Version 2 -> 3: adds reading-progress columns
    static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE books ADD COLUMN totalPages INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE books ADD COLUMN currentPage INTEGER NOT NULL DEFAULT 0");
        }
    };

    private static volatile AppDatabase INSTANCE;

    // Room does not allow writes on the main thread, so run inserts/updates/deletes here
    public static final ExecutorService databaseWriteExecutor =
            Executors.newFixedThreadPool(2);

    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    AppDatabase.class,
                                    "personal_library_db")
                            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}