package de.westnordost.streetcomplete.data

import co.touchlab.sqliter.Cursor
import co.touchlab.sqliter.DatabaseConfiguration
import co.touchlab.sqliter.DatabaseConnection
import co.touchlab.sqliter.Statement
import co.touchlab.sqliter.createDatabaseManager
import co.touchlab.sqliter.getBytesOrNull
import co.touchlab.sqliter.getColumnIndexOrThrow
import co.touchlab.sqliter.getDoubleOrNull
import co.touchlab.sqliter.getLongOrNull
import co.touchlab.sqliter.getStringOrNull
import co.touchlab.sqliter.withStatement

/** Opens (creating/upgrading as needed) the app's SQLite database on iOS, backed by
 *  co.touchlab:sqliter -- the same Kotlin/Native SQLite wrapper SQLDelight's own native driver
 *  is built on. Schema creation/migration is entirely shared with Android via
 *  [DatabaseInitializer]. */
fun createIosDatabase(name: String): Database {
    val manager = createDatabaseManager(
        DatabaseConfiguration(
            name = name,
            version = DatabaseInitializer.DB_VERSION,
            create = { connection -> DatabaseInitializer.onCreate(SqliterDatabase(connection)) },
            upgrade = { connection, oldVersion, newVersion ->
                DatabaseInitializer.onUpgrade(SqliterDatabase(connection), oldVersion, newVersion)
            },
        )
    )
    return SqliterDatabase(manager.createMultiThreadedConnection())
}

/** Implementation of [Database] using SQLite via co.touchlab:sqliter. Mirrors AndroidDatabase's
 *  behavior as closely as possible: same SQL text built for query/insert/update, same
 *  distinction between typed value binding (insert/update values) vs. stringified arg binding
 *  (where/exec args), same transaction-commits-only-if-everything-succeeded semantics. */
class SqliterDatabase(private val connection: DatabaseConnection) : Database {

    // sqliter's DatabaseConnection.beginTransaction()/endTransaction() do not support nesting
    // (unlike Android's SQLiteDatabase, which any transaction() call in this codebase may rely
    // on transitively). Nesting is emulated here instead: only the outermost transaction() call
    // actually issues BEGIN/COMMIT/ROLLBACK; inner calls just track whether everything
    // succeeded. If any nested call fails, the whole outermost transaction rolls back -- same
    // semantics as AndroidDatabase (backed by androidx.core.database.sqlite.transaction).
    // NOT thread-safe: relies on transaction() only ever being called from one thread at a time.
    private var transactionDepth = 0
    private var transactionFailed = false

    override fun exec(sql: String, args: Array<Any>?) {
        if (args == null) {
            connection.rawExecSql(sql)
        } else {
            connection.withStatement(sql) {
                bindArgs(args)
                execute()
            }
        }
    }

    override fun <T> rawQuery(sql: String, args: Array<Any>?, transform: (CursorPosition) -> T): List<T> {
        val statement = connection.createStatement(sql)
        try {
            if (args != null) statement.bindArgs(args)
            return statement.query().toList(transform)
        } finally {
            statement.finalizeStatement()
        }
    }

    override fun <T> queryOne(
        table: String,
        columns: Array<String>?,
        where: String?,
        args: Array<Any>?,
        groupBy: String?,
        having: String?,
        orderBy: String?,
        transform: (CursorPosition) -> T
    ): T? =
        query(table, columns, where, args, groupBy, having, orderBy, limit = 1, distinct = false, transform = transform).firstOrNull()

    override fun <T> query(
        table: String,
        columns: Array<String>?,
        where: String?,
        args: Array<Any>?,
        groupBy: String?,
        having: String?,
        orderBy: String?,
        limit: Int?,
        distinct: Boolean,
        transform: (CursorPosition) -> T
    ): List<T> {
        val sql = buildSelect(table, columns, where, groupBy, having, orderBy, limit, distinct)
        return rawQuery(sql, args, transform)
    }

    override fun insert(
        table: String,
        values: Collection<Pair<String, Any?>>,
        conflictAlgorithm: ConflictAlgorithm?
    ): Long {
        val sql = buildInsert(table, values.map { it.first }, conflictAlgorithm)
        return connection.withStatement(sql) {
            bindValues(values.map { it.second })
            executeInsert()
        }
    }

    override fun insertMany(
        table: String,
        columnNames: Array<String>,
        valuesList: Iterable<Array<Any?>>,
        conflictAlgorithm: ConflictAlgorithm?
    ): List<Long> {
        val sql = buildInsert(table, columnNames.toList(), conflictAlgorithm)
        val result = ArrayList<Long>()
        transaction {
            connection.withStatement(sql) {
                for (values in valuesList) {
                    require(values.size == columnNames.size)
                    bindValues(values.toList())
                    result.add(executeInsert())
                }
            }
        }
        return result
    }

    override fun update(
        table: String,
        values: Collection<Pair<String, Any?>>,
        where: String?,
        args: Array<Any>?,
        conflictAlgorithm: ConflictAlgorithm?
    ): Int {
        val sql = buildUpdate(table, values.map { it.first }, where, conflictAlgorithm)
        return connection.withStatement(sql) {
            bindValues(values.map { it.second })
            bindArgs(args, startIndex = values.size + 1)
            executeUpdateDelete()
        }
    }

    override fun delete(table: String, where: String?, args: Array<Any>?): Int {
        val sql = buildString {
            append("DELETE FROM ").append(table)
            if (where != null) append(" WHERE ").append(where)
        }
        return connection.withStatement(sql) {
            bindArgs(args)
            executeUpdateDelete()
        }
    }

    override fun <T> transaction(block: () -> T): T {
        val isOutermost = transactionDepth == 0
        if (isOutermost) {
            connection.beginTransaction()
            transactionFailed = false
        }
        transactionDepth++
        var successful = false
        try {
            val result = block()
            successful = true
            return result
        } finally {
            transactionDepth--
            if (!successful) transactionFailed = true
            if (transactionDepth == 0) {
                try {
                    if (!transactionFailed) connection.setTransactionSuccessful()
                } finally {
                    connection.endTransaction()
                }
            }
        }
    }
}

private fun buildSelect(
    table: String,
    columns: Array<String>?,
    where: String?,
    groupBy: String?,
    having: String?,
    orderBy: String?,
    limit: Int?,
    distinct: Boolean,
): String = buildString {
    append("SELECT ")
    if (distinct) append("DISTINCT ")
    append(columns?.joinToString(",") ?: "*")
    append(" FROM ").append(table)
    if (where != null) append(" WHERE ").append(where)
    if (groupBy != null) append(" GROUP BY ").append(groupBy)
    if (having != null) append(" HAVING ").append(having)
    if (orderBy != null) append(" ORDER BY ").append(orderBy)
    if (limit != null) append(" LIMIT ").append(limit)
}

private fun buildInsert(table: String, columnNames: List<String>, conflictAlgorithm: ConflictAlgorithm?): String {
    val columnsStr = columnNames.joinToString(",")
    val placeholdersStr = columnNames.joinToString(",") { "?" }
    return "INSERT${conflictAlgorithm.toSql()} INTO $table ($columnsStr) VALUES ($placeholdersStr)"
}

private fun buildUpdate(
    table: String,
    columnNames: List<String>,
    where: String?,
    conflictAlgorithm: ConflictAlgorithm?
): String = buildString {
    append("UPDATE").append(conflictAlgorithm.toSql()).append(" ").append(table)
    append(" SET ").append(columnNames.joinToString(",") { "$it = ?" })
    if (where != null) append(" WHERE ").append(where)
}

private fun ConflictAlgorithm?.toSql(): String = when (this) {
    ConflictAlgorithm.ROLLBACK -> " OR ROLLBACK"
    ConflictAlgorithm.ABORT -> " OR ABORT"
    ConflictAlgorithm.FAIL -> " OR FAIL"
    ConflictAlgorithm.IGNORE -> " OR IGNORE"
    ConflictAlgorithm.REPLACE -> " OR REPLACE"
    null -> ""
}

/** Binds [args] as text, mirroring AndroidDatabase: where/exec args always go through Android's
 *  String[] selectionArgs, so they're bound the same (stringified) way here for consistency. */
private fun Statement.bindArgs(args: Array<Any>?, startIndex: Int = 1) {
    if (args == null) return
    for ((i, arg) in args.withIndex()) {
        bindString(startIndex + i, primitiveToString(arg))
    }
}

private fun primitiveToString(any: Any): String = when (any) {
    is Short, is Int, is Long, is Float, is Double -> any.toString()
    is String -> any
    else -> throw IllegalArgumentException("Cannot bind $any: Must be either Int, Long, Float, Double or String")
}

/** Binds [values] with their native SQLite type affinity, mirroring AndroidDatabase's
 *  SQLiteStatement.bind (used for insert/update, unlike where/exec args). */
private fun Statement.bindValues(values: List<Any?>, startIndex: Int = 1) {
    for ((i, value) in values.withIndex()) {
        bindValue(startIndex + i, value)
    }
}

private fun Statement.bindValue(index: Int, value: Any?) {
    when (value) {
        null -> bindNull(index)
        is String -> bindString(index, value)
        is Double -> bindDouble(index, value)
        is Long -> bindLong(index, value)
        is ByteArray -> bindBlob(index, value)
        is Int -> bindLong(index, value.toLong())
        is Short -> bindLong(index, value.toLong())
        is Float -> bindDouble(index, value.toDouble())
        else -> throw IllegalArgumentException("Illegal value type ${value::class} at column $index")
    }
}

private fun <T> Cursor.toList(transform: (CursorPosition) -> T): List<T> {
    val position = SqliterCursorPosition(this)
    val result = ArrayList<T>()
    while (next()) {
        result.add(transform(position))
    }
    return result
}

private class SqliterCursorPosition(private val cursor: Cursor) : CursorPosition {
    override fun getInt(columnName: String): Int = cursor.getLong(index(columnName)).toInt()
    override fun getLong(columnName: String): Long = cursor.getLong(index(columnName))
    override fun getDouble(columnName: String): Double = cursor.getDouble(index(columnName))
    override fun getFloat(columnName: String): Float = cursor.getDouble(index(columnName)).toFloat()
    override fun getBlob(columnName: String): ByteArray = cursor.getBytes(index(columnName))
    override fun getString(columnName: String): String = cursor.getString(index(columnName))
    override fun getIntOrNull(columnName: String): Int? = cursor.getLongOrNull(index(columnName))?.toInt()
    override fun getLongOrNull(columnName: String): Long? = cursor.getLongOrNull(index(columnName))
    override fun getDoubleOrNull(columnName: String): Double? = cursor.getDoubleOrNull(index(columnName))
    override fun getFloatOrNull(columnName: String): Float? = cursor.getDoubleOrNull(index(columnName))?.toFloat()
    override fun getBlobOrNull(columnName: String): ByteArray? = cursor.getBytesOrNull(index(columnName))
    override fun getStringOrNull(columnName: String): String? = cursor.getStringOrNull(index(columnName))

    private fun index(columnName: String): Int = cursor.getColumnIndexOrThrow(columnName)
}
