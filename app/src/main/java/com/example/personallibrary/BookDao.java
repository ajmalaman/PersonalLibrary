package com.example.personallibrary;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface BookDao {

    @Insert
    long insert(Book book);

    @Update
    void update(Book book);

    @Delete
    void delete(Book book);

    // LiveData: the UI refreshes automatically whenever the table changes
    @Query("SELECT * FROM books ORDER BY title COLLATE NOCASE ASC")
    LiveData<List<Book>> getAllBooks();
}