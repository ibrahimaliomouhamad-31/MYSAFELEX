# MYSAFELEX v1.4 — Release notes + déploiement

Date : 26/09/2026 — versionCode 4, versionName 1.4.
Forfait Firebase : Spark (gratuit), aucun serveur, aucun Storage.

## Contenu de la release
- Console direction `direction-console.html` : temps réel, déclencher/arrêter
  l'alarme, verrouiller l'écran (`lock=true`), historique GPS (200 points),
  recherche + filtre, export CSV (global + par historique).
- Historique GPS : `devices/{matricule}/track/{timestamp}`, nettoyage auto 200 pts.
- Verrouillage à distance via DeviceAdmin + acquittement `lock=false`.
- SMS de secours : position + lien Maps au numéro de secours si Firestore
  injoignable (permission SEND_SMS conditionnelle, anti-spam 1 SMS / 10 min).
- Design : splash animé (SplashActivity 1,2 s), icône adaptative
  (bouclier + cadenas), thème Material3 jour/nuit.
- Sécurité : PIN hashé (SecurityUtils), `firestore.rules` (staff, `status`/`lock`
  uniquement pour la direction, `track/` propriétaire en écriture).

## Déploiement pas-à-pas (direction)
1. Firebase Console → Authentication → Users : créer chaque compte direction
   (email + mot de passe), noter l'UID de chacun.
2. Firestore → créer la collection `staff`, un document par compte :
   ID = UID du compte, champ `role: "direction"`.
3. Firestore Database → Règles : copier TOUT le contenu de `firestore.rules`,
   Publier. Vérifier l'heure de publication affichée par la console.
4. Paramètres du projet → Vos applications → Web : copier `apiKey`,
   `authDomain`, `projectId` dans `firebaseConfig` de `direction-console.html`.
5. Ouvrir `direction-console.html` (double-clic), se connecter : on doit voir
   « N appareil(s) surveillé(s) ». Sinon : règles non publiées ou `staff/{uid}`
   manquant.
6. Android Studio : Sync Gradle → Build → Generate Signed Bundle/APK
   (ou Run sur téléphone). Tester : inscription, bouclier vert, alarme via
   console (`status=vole`), verrouillage (`lock=true`), historique, arrêt PIN,
   SMS secours (mode avion + Wi-Fi coupée).
7. Restreindre la clé API Android (Cloud Console) : package `com.mysafelex` +
   empreinte SHA-1 du certificat de signature.

## Rollback
- Règles : republier la version précédente depuis l'historique des règles.
- Appli : réinstaller l'APK v1.3 (tag/commit précédent).
- Console : aucun déploiement serveur, le fichier HTML est local.

## Accès Git (dépôt GitHub)
- Dépôt : `https://github.com/ibrahimaliomouhamad-31/MYSAFELEX.git`
- `origin` est configuré en
  `https://x-access-token@github.com/ibrahimaliomouhamad-31/MYSAFELEX.git` :
  le nom d'utilisateur `x-access-token` indique à Git Credential Manager (GCM)
  quel identifiant utiliser pour les pousses suivantes.
- Le **jeton d'accès (PAT)** est stocké **chiffré dans le Gestionnaire
  d'identifiants Windows** (cible `git:https://x-access-token@github.com`).
  Il n'est écrit **ni dans `.git/config`**, ni dans un fichier versionné.
- Vérifier l'entrée enregistrée : `cmdkey /list | Select-String github`
- Changer / (re)mettre un jeton :
  `"protocol=https`nhost=github.com`nusername=x-access-token`npassword=NOUVEAU_JETON`n`n" | git credential approve`
- Supprimer un jeton : révoquer sur https://github.com/settings/tokens puis
  `cmdkey /delete:git:https://x-access-token@github.com`
- ⚠️ **Un jeton qui a circulé dans une conversation, un ticket ou l'historique
  d'un terminal doit être considéré comme exposé** : révoquez-le et créez-en un
  nouveau à portée minimale (« Contents: Read and write » sur ce seul dépôt).
- Astuce : `git config --global core.pager cat` évite que le terminal reste
  bloqué sur le pager (`less`, invite `:`) après un `git log` ou `git diff`.

## Fichiers clés
- `direction-console.html`, `firestore.rules`, `README.md`
- `app/src/main/java/com/mysafelex/SecurityActions.java`
- `app/src/main/java/com/mysafelex/SplashActivity.java`
- `app/src/main/res/mipmap-anydpi-v26/ic_launcher*.xml`
- `app/src/main/res/drawable/ic_launcher_foreground.xml`
