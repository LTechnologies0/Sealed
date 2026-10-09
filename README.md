# Sealed

Alternative Android à [Spider](https://github.com/Zaedus/spider) : chaque site « installé » tourne dans son propre profil WebView.

## Isolation

Android System WebView expose des profils (`ProfileStore`, `WebViewCompat.setProfile`). Sealed crée un profil par site. Ce profil possède ses propres cookies, `localStorage`, service workers et permissions de géolocalisation. Les cookies tiers sont refusés. La navigation peut être limitée au domaine d’origine et à ses sous-domaines. Le trafic en clair est interdit.

Supprimer une app appelle `ProfileStore.deleteProfile`, ce qui efface les données de ce profil.

Il faut un WebView récent qui déclare `MULTI_PROFILE`. Sinon l’app refuse d’ouvrir un site plutôt que de tout mélanger dans le profil par défaut.

## Limites

Le cache HTTP et certaines métadonnées du processus WebView (adresse IP, fingerprint du moteur) ne sont pas un bac à sable système : les apps partagent le même binaire WebView. L’isolation porte sur les données de site, pas sur le réseau.

## Build

```
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease
```

La release produit un APK par architecture : `armeabi-v7a`, `arm64-v8a`, `x86`, `x86_64`. Sur un téléphone, installer **arm64-v8a**. Sans keystore, l’APK release n’est pas signé.

## MTE

`android:memtagMode="async"` est intégré à l’application release. Le debug force `sync`. Sur un appareil ARM avec MTE (Pixel 8+, GrapheneOS), le système active le marquage mémoire du processus. Ailleurs l’attribut est ignoré. `scripts/verify-memtag-manifest.sh` échoue si une fusion de manifeste le retire.

## Signature CI

Secrets du dépôt : `RELEASE_KEYSTORE_BASE64`, `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`. En local, copier `keystore.properties.example` vers `keystore.properties`. Le workflow Release (tag `v*.*.*` ou lancement manuel avec un tag) publie les quatre APK signés et `SHA256SUMS.txt`.
