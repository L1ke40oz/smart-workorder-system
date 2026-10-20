package com.example.smart_workorder_system.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import com.example.smart_workorder_system.BuildConfig;
import com.google.gson.Gson;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Tokens are encrypted with an Android Keystore key, never logged or backed up. */
public class SessionStore {
    private static final String KEY_ALIAS="workorder_session_key";
    private final SharedPreferences prefs;
    private final Gson gson=new Gson();
    private volatile String token;
    public SessionStore(Context context) {
        prefs=context.getSharedPreferences("session",Context.MODE_PRIVATE);
        try {
            String cipherText=prefs.getString("token",null);
            if(cipherText!=null) {
                Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
                cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(prefs.getString("iv",""),Base64.NO_WRAP)));
                token=new String(cipher.doFinal(Base64.decode(cipherText,Base64.NO_WRAP)),StandardCharsets.UTF_8);
            }
        } catch(Exception e) { clear(); }
    }
    private SecretKey key() throws Exception {
        KeyStore store=KeyStore.getInstance("AndroidKeyStore"); store.load(null);
        if(store.containsAlias(KEY_ALIAS)) return (SecretKey)store.getKey(KEY_ALIAS,null);
        KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
        return generator.generateKey();
    }
    public String token() { return token; }
    public Models.User user() {
        if(token==null) return null;
        try { return gson.fromJson(prefs.getString("user",null),Models.User.class); }
        catch(Exception e) { clear(); return null; }
    }
    public void save(Models.Session session) throws Exception {
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE,key());
        String encrypted=Base64.encodeToString(cipher.doFinal(session.token.getBytes(StandardCharsets.UTF_8)),Base64.NO_WRAP);
        prefs.edit().putString("token",encrypted).putString("iv",Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP))
                .putString("user",gson.toJson(session.user)).apply();
        token=session.token;
    }
    public void updateUser(Models.User user) { prefs.edit().putString("user",gson.toJson(user)).apply(); }
    public void clear() { token=null; prefs.edit().remove("token").remove("iv").remove("user").apply(); }
    public String baseUrl() { return prefs.getString("baseUrl",BuildConfig.API_BASE_URL); }
    public void baseUrl(String url) { clear(); prefs.edit().putString("baseUrl",url).apply(); }
}
