package com.example.test

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import com.example.utils.SupabaseSetup
import kotlinx.coroutines.runBlocking
import io.github.jan.supabase.auth.providers.builtin.OTP

fun test() = runBlocking {
    val supabase = SupabaseSetup.client
    supabase.auth.signUpWith(Email) {
        this.email = "test@test.com"
        this.password = "password"
    }
    supabase.auth.verifyEmailOtp(io.github.jan.supabase.auth.OtpType.Email.SIGNUP, "test@test.com", "123456")
}
