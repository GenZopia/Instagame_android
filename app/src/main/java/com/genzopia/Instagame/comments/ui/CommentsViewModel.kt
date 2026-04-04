package com.genzopia.Instagame.comments.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.genzopia.Instagame.comments.data.CommentsRepository
import com.genzopia.Instagame.comments.models.Comment
import com.genzopia.Instagame.comments.models.Reply
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

/**
 * Single source of truth — mirrors web's likedVideos Set + optimistic update pattern.
 * No dislike. Reply works like web: postReply under the parent comment.
 */
class CommentsViewModel : ViewModel() {

    private val repo = CommentsRepository()
    private val auth = FirebaseAuth.getInstance()

    private var videoId: String? = null
    private var lastCreatedAt: Long? = null
    private var hasMore = true

    // ── Comments ───────────────────────────────────────────────────────────────
    private val _comments = MutableLiveData<List<Comment>>(emptyList())
    val comments: LiveData<List<Comment>> = _comments

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    // ── Liked sets (mirrors web likedVideos Set<string>) ──────────────────────
    private val _likedComments = MutableLiveData<Set<String>>(emptySet())
    val likedComments: LiveData<Set<String>> = _likedComments

    private val _likedReplies = MutableLiveData<Set<String>>(emptySet())
    val likedReplies: LiveData<Set<String>> = _likedReplies

    // ── In-flight debounce (mirrors web followOperations Set) ─────────────────
    private val _inFlightComments = MutableLiveData<Set<String>>(emptySet())
    val inFlightComments: LiveData<Set<String>> = _inFlightComments

    private val _inFlightReplies = MutableLiveData<Set<String>>(emptySet())
    val inFlightReplies: LiveData<Set<String>> = _inFlightReplies

    // ── Replies per comment ────────────────────────────────────────────────────
    private val _repliesMap = MutableLiveData<Map<String, List<Reply>>>(emptyMap())
    val repliesMap: LiveData<Map<String, List<Reply>>> = _repliesMap

    // ── Reply posted event — fires commentId so Fragment can auto-expand replies ─
    private val _replyPostedEvent = MutableLiveData<String?>()
    val replyPostedEvent: LiveData<String?> = _replyPostedEvent
    fun clearReplyPostedEvent() { _replyPostedEvent.value = null }

    // ── Reply-to state (mirrors web replyingTo state) ─────────────────────────
    private val _replyingTo = MutableLiveData<Comment?>(null)
    val replyingTo: LiveData<Comment?> = _replyingTo

    // ── One-shot error events ──────────────────────────────────────────────────
    private val _errorEvent = MutableLiveData<String?>()
    val errorEvent: LiveData<String?> = _errorEvent

    // ══════════════════════════════════════════════════════════════════════════
    // Init / load
    // ══════════════════════════════════════════════════════════════════════════

    fun init(videoId: String?) {
        if (videoId == null || this.videoId == videoId) return
        this.videoId = videoId
        loadFirstPage()
    }

    fun loadFirstPage() {
        val vid = videoId ?: return
        _isLoading.value = true
        repo.fetchCommentsFirstPage(vid, object : CommentsRepository.CommentsCallback {
            override fun onLoaded(list: List<Comment>, last: Long?, more: Boolean) {
                lastCreatedAt = last
                hasMore = more
                _comments.postValue(list)
                _isLoading.postValue(false)
                checkLikedBatch(list)
            }
            override fun onError(msg: String) {
                _isLoading.postValue(false)
                _errorEvent.postValue(msg)
            }
        })
    }

    fun loadNextPage() {
        val vid = videoId ?: return
        if (_isLoading.value == true || !hasMore) return
        _isLoading.value = true
        repo.fetchCommentsNextPage(vid, lastCreatedAt, object : CommentsRepository.CommentsCallback {
            override fun onLoaded(more: List<Comment>, last: Long?, hasMoreMore: Boolean) {
                lastCreatedAt = last
                hasMore = hasMoreMore
                val merged = (_comments.value ?: emptyList()) + more
                _comments.postValue(merged)
                _isLoading.postValue(false)
                checkLikedBatch(more)
            }
            override fun onError(msg: String) {
                _isLoading.postValue(false)
                _errorEvent.postValue(msg)
            }
        })
    }

    private fun checkLikedBatch(list: List<Comment>) {
        val uid = auth.currentUser?.uid ?: return
        val vid = videoId ?: return
        list.forEach { c ->
            if (c.comment_id == null) return@forEach
            repo.isCommentLiked(vid, c.comment_id, uid) { liked ->
                if (liked) _likedComments.postValue((_likedComments.value ?: emptySet()) + c.comment_id)
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Comment like — optimistic update + revert on error (exact web pattern)
    // ══════════════════════════════════════════════════════════════════════════

    fun toggleCommentLike(comment: Comment) {
        val uid = auth.currentUser?.uid ?: return
        val vid = videoId ?: return
        val cid = comment.comment_id ?: return

        val wasLiked = (_likedComments.value ?: emptySet()).contains(cid)

        // Update liked set
        _likedComments.value = if (!wasLiked)
            (_likedComments.value ?: emptySet()) + cid
        else
            (_likedComments.value ?: emptySet()) - cid

        repo.setCommentLike(vid, cid, uid, !wasLiked) { _, _ -> }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Reply like
    // ══════════════════════════════════════════════════════════════════════════

    fun toggleReplyLike(commentId: String, reply: Reply) {
        val uid = auth.currentUser?.uid ?: return
        val vid = videoId ?: return
        val rid = reply.reply_id ?: return

        val wasLiked = (_likedReplies.value ?: emptySet()).contains(rid)

        _likedReplies.value = if (!wasLiked)
            (_likedReplies.value ?: emptySet()) + rid
        else
            (_likedReplies.value ?: emptySet()) - rid

        repo.setReplyLike(vid, commentId, rid, uid, !wasLiked) { _, _ -> }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Replies
    // ══════════════════════════════════════════════════════════════════════════

    fun loadReplies(commentId: String) {
        val vid = videoId ?: return
        repo.fetchRepliesFirstPage(vid, commentId, object : CommentsRepository.RepliesCallback {
            override fun onLoaded(replies: List<Reply>, last: Long?, more: Boolean) {
                _repliesMap.postValue((_repliesMap.value ?: emptyMap()) + (commentId to replies))
                val uid = auth.currentUser?.uid ?: return
                replies.forEach { r ->
                    repo.isReplyLiked(vid, commentId, r.reply_id, uid) { liked ->
                        if (liked) _likedReplies.postValue((_likedReplies.value ?: emptySet()) + r.reply_id)
                    }
                }
            }
            override fun onError(msg: String) { _errorEvent.postValue(msg) }
        })
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Reply-to state (mirrors web replyingTo useState)
    // ══════════════════════════════════════════════════════════════════════════

    fun setReplyingTo(comment: Comment?) { _replyingTo.value = comment }

    // ══════════════════════════════════════════════════════════════════════════
    // Post comment / reply
    // ══════════════════════════════════════════════════════════════════════════

    fun postComment(text: String) {
        val uid = auth.currentUser?.uid ?: return
        val vid = videoId ?: return
        if (text.isBlank()) return

        val temp = Comment().apply {
            comment_id = "temp_${System.currentTimeMillis()}"
            user_id = uid
            user_display_name = auth.currentUser?.displayName ?: "You"
            user_photo_url = auth.currentUser?.photoUrl?.toString()
            this.text = text
            created_at = System.currentTimeMillis()
            like_count = 0L; reply_count = 0L
        }
        _comments.value = listOf(temp) + (_comments.value ?: emptyList())

        resolveUserProfile(uid) { name, photo ->
            repo.postComment(vid, text, uid, name, photo) { success, error ->
                if (success) loadFirstPage()
                else {
                    _comments.postValue((_comments.value ?: emptyList()).filter { it.comment_id != temp.comment_id })
                    _errorEvent.postValue(error ?: "Failed to post comment")
                }
            }
        }
    }

    fun postReply(commentId: String, text: String) {
        val uid = auth.currentUser?.uid ?: return
        val vid = videoId ?: return
        if (text.isBlank()) return

        resolveUserProfile(uid) { name, photo ->
            repo.postReply(vid, commentId, text, uid, name, photo) { success, error ->
                if (success) {
                    // Increment reply_count optimistically on the parent comment
                    val updated = (_comments.value ?: emptyList()).map { c ->
                        if (c.comment_id == commentId) {
                            c.reply_count = (c.reply_count ?: 0L) + 1L; c
                        } else c
                    }
                    _comments.postValue(updated)
                    loadReplies(commentId)
                    _replyPostedEvent.postValue(commentId)
                } else {
                    _errorEvent.postValue(error ?: "Failed to post reply")
                }
            }
        }
    }

    fun reportComment(commentId: String) {
        val uid = auth.currentUser?.uid ?: return
        val vid = videoId ?: return
        FirebaseDatabase.getInstance().getReference("videos").child(vid)
            .child("comment_reports").child(commentId).child(uid).setValue(true)
    }

    fun clearError() { _errorEvent.value = null }

    // ══════════════════════════════════════════════════════════════════════════
    // Helpers
    // ══════════════════════════════════════════════════════════════════════════

    private fun resolveUserProfile(uid: String, cb: (name: String?, photo: String?) -> Unit) {
        FirebaseDatabase.getInstance().getReference("users").child(uid)
            .get().addOnSuccessListener { snap ->
                val name = snap.child("full_name").getValue(String::class.java)
                    ?: snap.child("username").getValue(String::class.java)
                val photo = snap.child("profile_photo_url").getValue(String::class.java)
                cb(name, photo)
            }.addOnFailureListener { cb(null, null) }
    }

    private fun notifyCommentChanged(comment: Comment) {
        _comments.postValue(_comments.value?.map {
            if (it.comment_id == comment.comment_id) comment else it
        } ?: emptyList())
    }

    private fun notifyReplyChanged(commentId: String, reply: Reply) {
        val map = _repliesMap.value?.toMutableMap() ?: return
        map[commentId] = map[commentId]?.map { if (it.reply_id == reply.reply_id) reply else it } ?: return
        _repliesMap.postValue(map)
    }
}
