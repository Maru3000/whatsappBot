package com.maru.expenserecorder.auth

import android.app.Activity
import android.content.Context
import com.maru.expenserecorder.R
import com.microsoft.identity.client.*
import com.microsoft.identity.client.exception.MsalException
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class MicrosoftAuthManager(private val context: Context) {

    companion object {
        val SCOPES = listOf("Files.ReadWrite", "User.Read")
    }

    private var pca: ISingleAccountPublicClientApplication? = null

    suspend fun initialize(): Boolean = suspendCoroutine { cont ->
        PublicClientApplication.createSingleAccountPublicClientApplication(
            context,
            R.raw.auth_config,
            object : IPublicClientApplication.ISingleAccountApplicationCreatedListener {
                override fun onCreated(app: ISingleAccountPublicClientApplication) {
                    pca = app
                    cont.resume(true)
                }
                override fun onError(e: MsalException) = cont.resume(false)
            }
        )
    }

    suspend fun signIn(activity: Activity): String? = suspendCoroutine { cont ->
        pca?.signIn(activity, null, SCOPES.toTypedArray(), object : AuthenticationCallback {
            override fun onSuccess(result: IAuthenticationResult) = cont.resume(result.accessToken)
            override fun onError(e: MsalException) = cont.resume(null)
            override fun onCancel() = cont.resume(null)
        }) ?: cont.resume(null)
    }

    suspend fun getTokenSilently(): String? = suspendCoroutine { cont ->
        val app = pca ?: run { cont.resume(null); return@suspendCoroutine }
        app.getCurrentAccountAsync(object : ISingleAccountPublicClientApplication.CurrentAccountCallback {
            override fun onAccountLoaded(account: IAccount?) {
                if (account == null) { cont.resume(null); return }
                val params = AcquireTokenSilentParameters.Builder()
                    .forAccount(account)
                    .fromAuthority(account.authority)
                    .withScopes(SCOPES)
                    .build()
                app.acquireTokenSilentAsync(params, object : SilentAuthenticationCallback {
                    override fun onSuccess(result: IAuthenticationResult) = cont.resume(result.accessToken)
                    override fun onError(e: MsalException) = cont.resume(null)
                })
            }
            override fun onAccountChanged(prior: IAccount?, current: IAccount?) {}
            override fun onError(e: MsalException) = cont.resume(null)
        })
    }

    suspend fun getCurrentUserName(): String? = suspendCoroutine { cont ->
        pca?.getCurrentAccountAsync(object : ISingleAccountPublicClientApplication.CurrentAccountCallback {
            override fun onAccountLoaded(account: IAccount?) = cont.resume(account?.username)
            override fun onAccountChanged(prior: IAccount?, current: IAccount?) {}
            override fun onError(e: MsalException) = cont.resume(null)
        }) ?: cont.resume(null)
    }

    suspend fun signOut(): Unit = suspendCoroutine { cont ->
        pca?.signOut(object : ISingleAccountPublicClientApplication.SignOutCallback {
            override fun onSignOut() = cont.resume(Unit)
            override fun onError(e: MsalException) = cont.resume(Unit)
        }) ?: cont.resume(Unit)
    }
}
