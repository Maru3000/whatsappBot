package com.maru.expenserecorder.auth

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.services.sheets.v4.SheetsScopes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GoogleAuthManager(private val context: Context) {

    private val signInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestEmail()
        .requestScopes(Scope(SheetsScopes.SPREADSHEETS))
        .build()

    val signInClient: GoogleSignInClient = GoogleSignIn.getClient(context, signInOptions)

    fun isSignedIn(): Boolean = GoogleSignIn.getLastSignedInAccount(context) != null

    fun getAccountEmail(): String? = GoogleSignIn.getLastSignedInAccount(context)?.email

    fun buildCredential(): GoogleAccountCredential? {
        val signInAccount = GoogleSignIn.getLastSignedInAccount(context) ?: return null
        val androidAccount = signInAccount.account ?: return null
        return GoogleAccountCredential.usingOAuth2(context, listOf(SheetsScopes.SPREADSHEETS))
            .also { it.selectedAccount = androidAccount }
    }

    suspend fun signOut() = withContext(Dispatchers.IO) {
        signInClient.signOut()
    }
}
