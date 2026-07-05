package com.genzopia.Instagame.comments.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.genzopia.Instagame.R;
import com.genzopia.Instagame.channel_view.ChannelActivity;
import com.genzopia.Instagame.comments.models.Comment;
import com.genzopia.Instagame.comments.models.Reply;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;


import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Mirrors web behaviour exactly:
 *  - Like only (no dislike)
 *  - Tap "Reply" → shows "Replying to @name" banner + focuses input
 *  - Send posts reply under that comment; X on banner cancels back to comment mode
 */
public class CommentsBottomSheetFragment extends BottomSheetDialogFragment {

    public static CommentsBottomSheetFragment newInstance(String videoId) {
        Bundle b = new Bundle();
        b.putString("videoId", videoId);
        CommentsBottomSheetFragment f = new CommentsBottomSheetFragment();
        f.setArguments(b);
        return f;
    }

    private CommentsViewModel vm;
    private CommentsAdapter adapter;

    // Input views — kept as fields so observers can update them
    private EditText input;
    private ImageButton sendBtn;
    private View replyBanner;
    private TextView replyBannerText;
    private ImageButton replyBannerClose;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.bottomsheet_comments, container, false);

        String videoId = getArguments() != null ? getArguments().getString("videoId") : null;

        vm = new ViewModelProvider(this).get(CommentsViewModel.class);
        vm.init(videoId);

        // Input row
        input           = v.findViewById(R.id.comment_input);
        sendBtn         = v.findViewById(R.id.comment_send_btn);
        replyBanner     = v.findViewById(R.id.reply_banner);
        replyBannerText = v.findViewById(R.id.reply_banner_text);
        replyBannerClose= v.findViewById(R.id.reply_banner_close);

        // List
        RecyclerView list = v.findViewById(R.id.comments_list);
        list.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new CommentsAdapter(buildCommentListener());
        list.setAdapter(adapter);

        // Pagination
        list.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (!rv.canScrollVertically(1)) vm.loadNextPage();
            }
        });

        // ── Observe comments ──────────────────────────────────────────────────
        vm.getComments().observe(getViewLifecycleOwner(), comments -> adapter.submitList(comments));

        // No notifyDataSetChanged on like changes — ViewHolder manages its own local state

        // ── Auto-expand replies after posting a reply ─────────────────────────
        vm.getReplyPostedEvent().observe(getViewLifecycleOwner(), commentId -> {
            if (commentId != null) {
                adapter.expandReplies(commentId);
                vm.clearReplyPostedEvent();
            }
        });

        // ── Observe replyingTo — mirrors web replyingTo state ─────────────────
        vm.getReplyingTo().observe(getViewLifecycleOwner(), replyingTo -> {
            if (replyingTo != null) {
                // Show banner, update hint — same as web "Replying to @name" banner
                if (replyBanner != null) replyBanner.setVisibility(View.VISIBLE);
                if (replyBannerText != null)
                    replyBannerText.setText("Replying to " + (replyingTo.user_display_name != null ? replyingTo.user_display_name : "user"));
                input.setHint("Write a reply…");
                input.requestFocus();
                showKeyboard();
            } else {
                // Banner gone, back to comment mode
                if (replyBanner != null) replyBanner.setVisibility(View.GONE);
                input.setHint("Add a comment…");
            }
        });

        // ── Errors ────────────────────────────────────────────────────────────
        vm.getErrorEvent().observe(getViewLifecycleOwner(), msg -> {
            if (msg != null && getContext() != null) {
                Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
                vm.clearError();
            }
        });

        // ── Send button ───────────────────────────────────────────────────────
        sendBtn.setOnClickListener(view -> {
            String txt = input.getText() != null ? input.getText().toString().trim() : "";
            if (txt.isEmpty()) return;
            input.setText("");

            Comment replyingTo = vm.getReplyingTo().getValue();
            if (replyingTo != null) {
                // Post reply, then clear banner
                vm.postReply(replyingTo.comment_id, txt);
                vm.setReplyingTo(null);
            } else {
                vm.postComment(txt);
            }
        });

        // ── Banner close (X) — mirrors web "Cancel reply" ─────────────────────
        if (replyBannerClose != null) {
            replyBannerClose.setOnClickListener(view -> vm.setReplyingTo(null));
        }

        // ── Close sheet ───────────────────────────────────────────────────────
        View closeBtn = v.findViewById(R.id.comments_close);
        if (closeBtn != null) closeBtn.setOnClickListener(view -> dismiss());

        return v;
    }

    // ── Adapter listener ──────────────────────────────────────────────────────

    private CommentsAdapter.OnCommentActionListener buildCommentListener() {
        return new CommentsAdapter.OnCommentActionListener() {

            @Override
            public void onReply(@NonNull Comment c) {
                // Tell ViewModel we're replying — observer updates banner + input
                vm.setReplyingTo(c);
            }

            @Override
            public void onToggleLike(@NonNull Comment c) {
                vm.toggleCommentLike(c);
            }

            @Override
            public void checkLiked(@NonNull Comment c, @NonNull CommentsAdapter.LikeState state) {
                Set<String> liked = vm.getLikedComments().getValue();
                state.setInitial(liked != null && liked.contains(c.comment_id));
            }

            @Override
            public void onLoadReplies(@NonNull Comment c, @NonNull RecyclerView repliesList) {
                // Only set up layout + adapter once per RecyclerView instance
                if (repliesList.getLayoutManager() == null) {
                    repliesList.setLayoutManager(new LinearLayoutManager(getContext()));
                    RepliesAdapter repliesAdapter = new RepliesAdapter(buildReplyListener(c.comment_id));
                    repliesList.setAdapter(repliesAdapter);

                    vm.getRepliesMap().observe(getViewLifecycleOwner(), map -> {
                        List<Reply> replies = map.get(c.comment_id);
                        if (replies != null) repliesAdapter.submitList(replies);
                    });
                }
                // Always trigger a fresh load from Firebase
                vm.loadReplies(c.comment_id);
            }

            @Override
            public void onMenuClick(@NonNull Comment c, @NonNull android.view.View anchor) {
                PopupMenu popup = new PopupMenu(requireContext(), anchor);
                popup.getMenu().add(0, 1, 0, "View Profile");
                popup.getMenu().add(0, 2, 1, "Report");
                popup.setOnMenuItemClickListener(item -> {
                    if (item.getItemId() == 1) {
                        // View Profile → open ChannelActivity
                        if (c.user_id != null && !c.user_id.isEmpty()) {
                            Intent intent = new Intent(requireContext(), ChannelActivity.class);
                            intent.putExtra("developer_id", c.user_id);
                            startActivity(intent);
                        } else {
                            Toast.makeText(requireContext(), "Profile not available", Toast.LENGTH_SHORT).show();
                        }
                        return true;
                    } else if (item.getItemId() == 2) {
                        showReportDialog(c);
                        return true;
                    }
                    return false;
                });
                popup.show();
            }
        };
    }

    private RepliesAdapter.OnReplyActionListener buildReplyListener(String commentId) {
        return new RepliesAdapter.OnReplyActionListener() {
            @Override public void onToggleLike(@NonNull Reply r) {
                vm.toggleReplyLike(commentId, r);
            }
            @Override public void checkLiked(@NonNull Reply r, @NonNull RepliesAdapter.LikeState state) {
                Set<String> liked = vm.getLikedReplies().getValue();
                state.setInitial(liked != null && liked.contains(r.reply_id));
            }
        };
    }

    private void showKeyboard() {
        if (getContext() == null) return;
        InputMethodManager imm = (InputMethodManager) getContext()
                .getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
    }

    private void showReportDialog(@NonNull Comment comment) {
        if (getContext() == null) return;

        final String[] reasons = {
            "Select a reason…",
            "Hate speech or discrimination",
            "Harassment or bullying",
            "Spam or misleading content",
            "Nudity or sexual content",
            "Violence or dangerous content",
            "Misinformation",
            "Impersonation",
            "Other"
        };

        // Build dialog view
        View dialogView = LayoutInflater.from(requireContext())
                .inflate(android.R.layout.select_dialog_item, null, false);

        // Use AlertDialog.Builder with a custom layout
        android.widget.LinearLayout layout = new android.widget.LinearLayout(requireContext());
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad, pad, 0);

        // Comment preview
        TextView commentPreview = new TextView(requireContext());
        commentPreview.setText("\"" + (comment.text != null ? comment.text : "") + "\"");
        commentPreview.setTextSize(13f);
        commentPreview.setMaxLines(3);
        commentPreview.setEllipsize(android.text.TextUtils.TruncateAt.END);
        commentPreview.setTextColor(0xFF9E9E9E);
        commentPreview.setPadding(0, 0, 0, pad);
        layout.addView(commentPreview);

        // Reason label
        TextView reasonLabel = new TextView(requireContext());
        reasonLabel.setText("Reason for reporting");
        reasonLabel.setTextSize(13f);
        reasonLabel.setTextColor(0xFF757575);
        reasonLabel.setPadding(0, 0, 0, (int)(6 * getResources().getDisplayMetrics().density));
        layout.addView(reasonLabel);

        // Spinner
        Spinner spinner = new Spinner(requireContext());
        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_spinner_item,
                reasons
        );
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(spinnerAdapter);
        layout.addView(spinner);

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle("Report Comment")
                .setView(layout)
                .setPositiveButton("Submit", null) // set below to prevent auto-dismiss on invalid
                .setNegativeButton("Cancel", null)
                .create();

        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                int selectedPos = spinner.getSelectedItemPosition();
                if (selectedPos == 0) {
                    Toast.makeText(requireContext(), "Please select a reason", Toast.LENGTH_SHORT).show();
                    return;
                }
                String selectedReason = reasons[selectedPos];
                submitReport(comment, selectedReason);
                dialog.dismiss();
            });
        });

        dialog.show();

        // Style the positive button orange
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setTextColor(0xFFFF6B35);
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
                .setTextColor(0xFF9E9E9E);
    }

    private void submitReport(@NonNull Comment comment, @NonNull String reason) {
        String reporterId = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser() != null
                ? com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
        if (reporterId == null) {
            Toast.makeText(requireContext(), "You must be logged in to report", Toast.LENGTH_SHORT).show();
            return;
        }
        String videoId = getArguments() != null ? getArguments().getString("videoId", "") : "";
        java.util.Map<String, String> body = new java.util.HashMap<>();
        body.put("reason", reason);
        com.genzopia.Instagame.gateway.GatewayClient.INSTANCE.getCallApi()
                .reportComment(videoId, comment.comment_id, body)
                .enqueue(new retrofit2.Callback<Void>() {
                    @Override
                    public void onResponse(@androidx.annotation.NonNull retrofit2.Call<Void> call,
                                           @androidx.annotation.NonNull retrofit2.Response<Void> resp) {
                        if (isAdded()) {
                            Toast.makeText(requireContext(),
                                    resp.isSuccessful()
                                            ? "Report submitted. Thank you for keeping the community safe."
                                            : "Failed to submit report. Please try again.",
                                    resp.isSuccessful() ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT).show();
                        }
                    }
                    @Override
                    public void onFailure(@androidx.annotation.NonNull retrofit2.Call<Void> call,
                                          @androidx.annotation.NonNull Throwable t) {
                        if (isAdded())
                            Toast.makeText(requireContext(), "Failed to submit report.", Toast.LENGTH_SHORT).show();
                    }
                });
    }
}
