package com.rallytrax.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.rallytrax.app.data.local.entity.GridCellEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GridCellDao {

    @Query("SELECT * FROM grid_cells")
    fun getAllCells(): Flow<List<GridCellEntity>>

    @Query("SELECT * FROM grid_cells")
    suspend fun getAllCellsOnce(): List<GridCellEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCells(cells: List<GridCellEntity>)

    @Query("SELECT * FROM grid_cells WHERE cellId = :cellId")
    suspend fun getCellById(cellId: Long): GridCellEntity?

    @Query("DELETE FROM grid_cells")
    suspend fun deleteAllCells()
}
