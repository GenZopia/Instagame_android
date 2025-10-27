package com.genzopia.Instagame.LoginActivities

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class AvatarBottomSheetFragment : BottomSheetDialogFragment() {

    interface Listener {
        fun onAvatarSelected(resId: Int)
        fun onChooseFromGallery()
        fun onTakePhoto()
    }

    private var listener: Listener? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is Listener) listener = context else listener = null
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val v = inflater.inflate(com.genzopia.Instagame.R.layout.bottom_sheet_avatar_picker, container, false)

        val preview: ImageView = v.findViewById(com.genzopia.Instagame.R.id.imgAvatarPreview)
        val rv: RecyclerView = v.findViewById(com.genzopia.Instagame.R.id.rvAvatars)
        val btnGallery = v.findViewById<com.google.android.material.button.MaterialButton>(com.genzopia.Instagame.R.id.btnChooseFromGallery)
        val btnTake = v.findViewById<com.google.android.material.button.MaterialButton>(com.genzopia.Instagame.R.id.btnTakePhoto)
        val btnApply = v.findViewById<com.google.android.material.button.MaterialButton>(com.genzopia.Instagame.R.id.btnApplyAvatar)
        val btnCancel = v.findViewById<com.google.android.material.button.MaterialButton>(com.genzopia.Instagame.R.id.btnCancelAvatar)

        // Simple presets: use a few drawable resources already present in project as placeholders
        val presets = listOf(
            com.genzopia.Instagame.R.drawable.ic_profile_placeholder_bg,
            com.genzopia.Instagame.R.drawable.ic_avatar_rings,
            com.genzopia.Instagame.R.drawable.ic_add_plus
        ).filter { res ->
            try { resources.getDrawable(res, null); true } catch (_: Exception) { false }
        }

        val adapter = AvatarAdapter(presets) { resId ->
            // update preview
            preview.setImageResource(resId)
            // call listener immediately to auto-apply selection
            listener?.onAvatarSelected(resId)
            dismiss()
        }

        rv.layoutManager = GridLayoutManager(context, 3)
        rv.adapter = adapter

        btnGallery.setOnClickListener {
            listener?.onChooseFromGallery()
            dismiss()
        }

        btnTake.setOnClickListener {
            listener?.onTakePhoto()
            dismiss()
        }

        btnCancel.setOnClickListener { dismiss() }
        btnApply.setOnClickListener {
            // if they haven't selected, do nothing; otherwise host listener already called when clicked
            dismiss()
        }

        return v
    }

    override fun onDetach() {
        super.onDetach()
        listener = null
    }
}

