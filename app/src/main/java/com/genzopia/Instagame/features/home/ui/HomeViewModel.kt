import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.genzopia.Instagame.features.home.data.FollowingRepository
import com.genzopia.Instagame.features.home.domain.FollowedUser
import com.genzopia.Instagame.utils.DataPrefetchService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

class HomeViewModel : ViewModel() {

    private val followingRepository = FollowingRepository()

    private val _followedUsers = MutableStateFlow<List<FollowedUser>>(emptyList())
    val followedUsers = _followedUsers.asStateFlow()
    private val _followedUsersLoading = MutableStateFlow(true)
    val followedUsersLoading = _followedUsersLoading.asStateFlow()

    private val _games = MutableStateFlow<List<com.genzopia.Instagame.features.home.ui.HomeGameItem>>(emptyList())
    val games = _games.asStateFlow()
    private val _gamesLoading = MutableStateFlow(true)
    val gamesLoading = _gamesLoading.asStateFlow()

    init {
        loadFollowedUsers()
        loadGames()
    }

    // No-op — kept so existing call sites compile
    fun initializePlayer(context: Context) {}

    fun loadGames() {
        if (_games.value.isNotEmpty()) return
        _gamesLoading.value = true
        val db = com.google.firebase.database.FirebaseDatabase.getInstance()
        db.getReference("games").addListenerForSingleValueEvent(object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                val list = mutableListOf<com.genzopia.Instagame.features.home.ui.HomeGameItem>()
                var pending = snapshot.childrenCount.toInt()
                if (pending == 0) { _gamesLoading.value = false; return }
                for (gameSnap in snapshot.children) {
                    val gameId = gameSnap.key
                    if (gameId == null) { pending--; if (pending == 0) { _games.value = list.toList(); _gamesLoading.value = false }; continue }
                    val gameName = gameSnap.child("game_name").getValue(String::class.java) ?: "Unknown"
                    val description = gameSnap.child("description").getValue(String::class.java) ?: ""
                    val devId = gameSnap.child("user_id").getValue(String::class.java) ?: ""
                    val photoId = gameSnap.child("photo_id").getValue(String::class.java) ?: ""
                    fun addGame(imageUrl: String, devName: String, devPhoto: String) {
                        synchronized(list) {
                            list.add(com.genzopia.Instagame.features.home.ui.HomeGameItem(gameId, gameName, description, imageUrl, devId, devName, devPhoto))
                            pending--
                            if (pending == 0) { _games.value = list.toList(); _gamesLoading.value = false }
                        }
                    }
                    fun fetchWithPhoto(imageUrl: String) {
                        if (devId.isEmpty()) { addGame(imageUrl, "", ""); return }
                        db.getReference("users").child(devId).addListenerForSingleValueEvent(object : com.google.firebase.database.ValueEventListener {
                            override fun onDataChange(u: com.google.firebase.database.DataSnapshot) {
                                val devName = u.child("full_name").getValue(String::class.java) ?: u.child("username").getValue(String::class.java) ?: "Developer"
                                val devPhoto = com.genzopia.Instagame.utils.ProfilePhotoUtils.sanitize(u.child("profile_photo_url").getValue(String::class.java) ?: "") ?: ""
                                addGame(imageUrl, devName, devPhoto)
                            }
                            override fun onCancelled(e: com.google.firebase.database.DatabaseError) { addGame(imageUrl, "", "") }
                        })
                    }
                    if (photoId.isNotEmpty()) {
                        db.getReference("photos").child(photoId).addListenerForSingleValueEvent(object : com.google.firebase.database.ValueEventListener {
                            override fun onDataChange(photoSnap: com.google.firebase.database.DataSnapshot) {
                                val fileExt = photoSnap.child("file_ext").getValue(String::class.java) ?: photoSnap.child("file_name").getValue(String::class.java)?.substringAfterLast('.', "jpg") ?: "jpg"
                                Thread { fetchWithPhoto(com.genzopia.Instagame.utils.PhotoUrlResolver.resolveSync(photoId, fileExt) ?: "") }.start()
                            }
                            override fun onCancelled(e: com.google.firebase.database.DatabaseError) { fetchWithPhoto("") }
                        })
                    } else fetchWithPhoto("")
                }
            }
            override fun onCancelled(e: com.google.firebase.database.DatabaseError) { _gamesLoading.value = false }
        })
    }

    private fun loadFollowedUsers() {
        val cached = DataPrefetchService.getCachedFollowedUsers()
        if (cached != null) { _followedUsers.value = cached; _followedUsersLoading.value = false; return }
        viewModelScope.launch {
            followingRepository.getFollowedUsers()
                .catch { _followedUsersLoading.value = false }
                .collect { users -> _followedUsers.value = users; _followedUsersLoading.value = false }
        }
    }
}
