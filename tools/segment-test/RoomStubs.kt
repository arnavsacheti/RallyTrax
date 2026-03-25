// Minimal stubs for Room annotations so entities compile without Android SDK
package androidx.room

annotation class Entity(
    val tableName: String = "",
    val foreignKeys: Array<ForeignKey> = [],
    val indices: Array<Index> = [],
    val primaryKeys: Array<String> = [],
)

annotation class PrimaryKey
annotation class Index(vararg val value: String)

annotation class ForeignKey(
    val entity: kotlin.reflect.KClass<*> = Any::class,
    val parentColumns: Array<String> = [],
    val childColumns: Array<String> = [],
    val onDelete: Int = 0,
) {
    companion object {
        const val CASCADE = 5
    }
}
