package com.remotesend.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts

class OpenMultipleDocumentsWithWrite : ActivityResultContracts.OpenMultipleDocuments() {
    override fun createIntent(context: Context, input: Array<String>): Intent {
        return super.createIntent(context, input)
            .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
    }
}

class OpenDocumentTreeWithWrite : ActivityResultContracts.OpenDocumentTree() {
    override fun createIntent(context: Context, input: Uri?): Intent {
        return super.createIntent(context, input)
            .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
    }
}
