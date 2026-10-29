package dev.itszain.termuxfilemanager.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "chats")
data class ChatRecord(@PrimaryKey val id: String, val name: String, val provider: String,
    val sessionId: String = "", val autoExecute: Boolean? = null, val legacy: String = "{}")
@Entity(tableName = "messages", indices = [Index("chatId")], foreignKeys = [ForeignKey(
    entity = ChatRecord::class, parentColumns = ["id"], childColumns = ["chatId"], onDelete = ForeignKey.CASCADE)])
data class MessageRecord(@PrimaryKey val id: String, val chatId: String, val role: String,
    val text: String, val time: Long = System.currentTimeMillis(), val thinking: String = "",
    val status: String = "complete", val metadata: String = "{}")
@Entity(tableName = "sessions")
data class SessionRecord(@PrimaryKey val id: String, val name: String, val workdir: String = "~/",
    val output: String = "", val legacy: String = "{}")
@Entity(tableName = "commands", indices = [Index("ownerId")])
data class CommandRecord(@PrimaryKey(autoGenerate = true) val id: Long = 0, val ownerId: String,
    val label: String, val command: String, val workdir: String, val kind: String = "shell",
    val state: String = "pending", val stdout: String = "", val stderr: String = "", val exitCode: Int? = null,
    val created: Long = System.currentTimeMillis(), val completed: Long? = null)
@Entity(tableName = "places")
data class PlaceRecord(@PrimaryKey val path: String, val favorite: Boolean = false, val visited: Long = 0)
@Entity(tableName = "drafts")
data class DraftRecord(@PrimaryKey val path: String, val text: String, val fingerprint: String)
@Entity(tableName = "migration_markers")
data class MigrationMarker(@PrimaryKey val name: String)

@Dao
interface AppDao {
    @Query("SELECT * FROM chats ORDER BY rowid") fun chats(): Flow<List<ChatRecord>>
    @Query("SELECT * FROM chats ORDER BY rowid") suspend fun allChats(): List<ChatRecord>
    @Query("SELECT * FROM chats WHERE id=:id") suspend fun chat(id: String): ChatRecord?
    @Upsert suspend fun saveChat(chat: ChatRecord)
    @Query("DELETE FROM chats WHERE id=:id") suspend fun deleteChat(id: String)
    @Query("SELECT * FROM messages WHERE chatId=:id ORDER BY time, rowid") fun messages(id: String): Flow<List<MessageRecord>>
    @Query("SELECT * FROM messages WHERE chatId=:id ORDER BY time, rowid") suspend fun history(id: String): List<MessageRecord>
    @Upsert suspend fun saveMessage(message: MessageRecord)
    @Query("UPDATE messages SET status='interrupted' WHERE status IN ('streaming','running','awaiting_approval')") suspend fun interruptStreams()
    @Query("SELECT * FROM sessions ORDER BY rowid") fun sessions(): Flow<List<SessionRecord>>
    @Query("SELECT * FROM sessions ORDER BY rowid") suspend fun allSessions(): List<SessionRecord>
    @Query("SELECT * FROM sessions WHERE id=:id") suspend fun session(id: String): SessionRecord?
    @Upsert suspend fun saveSession(session: SessionRecord)
    @Query("DELETE FROM sessions WHERE id=:id") suspend fun deleteSession(id: String)
    @Query("DELETE FROM commands WHERE ownerId=:id") suspend fun deleteCommands(id: String)
    @Query("DELETE FROM commands WHERE kind='files' AND completed IS NOT NULL AND id NOT IN (SELECT id FROM commands WHERE kind='files' ORDER BY created DESC LIMIT 100)") suspend fun pruneFileResults()
    @Insert suspend fun insertCommand(command: CommandRecord): Long
    @Query("SELECT * FROM commands WHERE id=:id") suspend fun command(id: Long): CommandRecord?
    @Query("SELECT * FROM commands WHERE id=:id") fun observeCommand(id: Long): Flow<CommandRecord?>
    @Query("SELECT * FROM commands WHERE ownerId=:id ORDER BY created DESC LIMIT 200") fun commands(id: String): Flow<List<CommandRecord>>
    @Query("SELECT * FROM commands WHERE ownerId=:id ORDER BY created DESC LIMIT 10") suspend fun recentCommands(id: String): List<CommandRecord>
    @Query("UPDATE commands SET state=:state,stdout=:stdout,stderr=:stderr,exitCode=:exitCode,completed=:completed WHERE id=:id AND completed IS NULL")
    suspend fun completeCommand(id: Long, state: String, stdout: String, stderr: String, exitCode: Int, completed: Long): Int
    @Query("SELECT * FROM places ORDER BY favorite DESC,visited DESC LIMIT 100") fun places(): Flow<List<PlaceRecord>>
    @Query("SELECT * FROM places WHERE path=:path") suspend fun place(path: String): PlaceRecord?
    @Upsert suspend fun savePlace(place: PlaceRecord)
    @Query("SELECT * FROM drafts WHERE path=:path") suspend fun draft(path: String): DraftRecord?
    @Upsert suspend fun saveDraft(draft: DraftRecord)
    @Query("DELETE FROM drafts WHERE path=:path") suspend fun deleteDraft(path: String)
    @Query("SELECT COUNT(*) FROM migration_markers WHERE name=:name") suspend fun hasMarker(name: String): Int
    @Insert suspend fun mark(marker: MigrationMarker)
}

@Database(entities = [ChatRecord::class, MessageRecord::class, SessionRecord::class, CommandRecord::class,
    PlaceRecord::class, DraftRecord::class, MigrationMarker::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): AppDao
    companion object {
        fun create(context: Context) = Room.databaseBuilder(context, AppDatabase::class.java, "tfm-workspace.db").build()
    }
}
