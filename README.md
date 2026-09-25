# MYSAFELEX

Application anti-vol pour les téléphones des élèves de l'internat du lycée
d'excellence de Tessaoua.

Fonctionne entièrement sur le forfait Firebase gratuit (Spark) : pas de
Firebase Storage (payant depuis peu, même en usage minime), pas de carte
bancaire nécessaire. Les photos "secrètes" sont stockées directement en
Base64 dans Firestore. La fonctionnalité d'enregistrement audio a été
retirée (elle dépendait de Firebase Storage).

## ⚠️ Étape à faire manuellement

**Déployer les règles de sécurité Firestore** : copiez le contenu de
`firestore.rules` dans Firebase Console → Firestore Database → Règles, et
cliquez sur "Publier". Sans ça, l'authentification ajoutée dans le code ne
protège rien : la base reste ouverte tant que les règles ne sont pas
publiées côté serveur.

Pensez aussi à restreindre la clé API Android dans Google Cloud Console
(nom de package `com.mysafelex` + empreinte SHA-1 de votre certificat de
signature).

## Console direction (v1.4)

Ouvrez `direction-console.html` dans un navigateur (double-clic suffit) :
connectez-vous avec le **compte direction** (email/mot de passe Firebase Auth)
pour voir tous les appareils en temps réel, déclencher/arrêter l'alarme,
**verrouiller l'écran à distance** (`lock=true`) et consulter l'**historique
GPS** (200 derniers points, liens Google Maps).

Préparation (à faire une seule fois) :
1. Dans Firebase Console → Authentication → Users : créez chaque compte
   direction (email + mot de passe), notez son UID.
2. Dans Firestore : créez la collection `staff` et un document par compte,
   d'ID = UID du compte, avec un champ `role: "direction"`.
3. Déployez les nouvelles `firestore.rules` (Firestore Database → Règles →
   Publier) : elles autorisent la direction à lire les appareils +
   l'historique et à modifier **uniquement** `status` et `lock`.
4. Dans `direction-console.html` : remplacez `firebaseConfig`
   (`apiKey`, `authDomain`, `projectId`) par ceux de votre projet
   (Paramètres du projet → Vos applications → Web), puis rouvrez le fichier.

## SMS de secours (v1.4)

Si le voleur coupe Internet, Firestore est injoignable : l'appli envoie
alors un **SMS avec la position + lien Google Maps** au numéro de secours
saisi par l'élève (champ facultatif sur l'écran principal, 1 SMS / 10 min
maximum). Permission `SEND_SMS` demandée uniquement si un numéro est
enregistré. Coût : un SMS classique selon l'opérateur (pas de serveur).

## Historique des correctifs

Voir `MYSAFELEX_audit_code.md` pour le détail : authentification anonyme
Firebase, règles de sécurité Firestore, correction d'un contournement de
code PIN, protection contre le détournement de matricule, migration de la
caméra vers CameraX, passage des photos en Base64/Firestore (suppression de
Firebase Storage et de l'audio pour rester sur le forfait gratuit), gestion
des permissions refusées, et texte honnête du dialogue de configuration.

### v1.2 — Sécurité & stabilité (24/09/2026)
- PIN jamais stocké en clair : hash SHA-256 salé (nouveau `SecurityUtils`),
  migration automatique des anciens PIN, vérification dans
  `MainActivity` + `AlarmActivity`, validation du format matricule/PIN.
- `firestore.rules` durcies : création limitée au statut `securise`,
  interdiction d'écrire `photoBase64`/`pinHash`/`pin_code` à la création,
  `ownerUid` immuable, suppression interdite.
- Permissions Android 11-14 : `ACCESS_BACKGROUND_LOCATION` demandée
  séparément avec explication, `POST_NOTIFICATIONS` seulement sur Android 13+,
  exemption batterie demandée une seule fois, message si refusées.
- `TheftService` : état d'alarme restauré après redémarrage, `startForeground`
  protégé (plus de crash Android 12+), notification plein écran
  (`USE_FULL_SCREEN_INTENT`) car Android 10+ bloque l'ouverture directe de
  l'écran d'alarme, nettoyage WakeLock/sonnerie/GPS sans crash.
- `SimReceiver`/`BootReceiver` : `goAsync()` (plus de travail tué), empreinte
  SIM avec repli Android 10+ (`getSimSerialNumber` restreint), service relancé
  au boot uniquement si appareil enregistré.
- `CameraHelper` : repli caméra dorsale si pas de frontale, garde-fous
  matricule vide, `unbindAll` protégés.
- `MessagingService` : action `VOL_STOP`/`STOP` ajoutée, try-catch partout.
- Dépendances manquantes ajoutées (`cardview`, `listenablefuture`) qui
  faisaient crasher l'app au lancement.

### v1.3 — Design moderne (25/09/2026)
- Thème Material3 jour/nuit (`Theme.Mysafelex`, `values-night/colors.xml`) :
  le mode sombre suit le système, fini l'écran blanc aveuglant la nuit.
- Écran principal repensé : en-tête héro en dégradé rouge avec pastille
  version, carte statut avec pastille couleur (orange/vert/rouge),
  « Bouclier de protection » (localisation, photo, SIM, admin : OK/KO en
  direct), champs avec icônes + aides, bouton « Activer la protection »,
  bouton « Comment ça marche ? ».
- Écran d'alarme repensé (`activity_alarm_new`) : fond dégradé rouge/noir,
  badge « Vol détecté », carte vitrée avec champ PIN Material et bouton blanc.
- L'app s'appelle désormais **MYSAFELEX** partout (fini « Bloc-note »).
- `MainActivity.updateStatusCard()` met à jour le nouveau statut + bouclier
  à chaque `onResume`.

### v1.4 — Fonctionnalités + design (26/09/2026)
- **Console direction** (`direction-console.html`, 100 % forfait gratuit) :
  connexion email/mot de passe, temps réel (statut, position + lien Maps,
  photo, alerte SIM), recherche + filtre, déclencher/arrêter l'alarme,
  **verrouiller l'écran à distance**, **historique GPS** (200 points).
- **Historique GPS** : chaque position est aussi écrite dans
  `devices/{matricule}/track/{timestamp}` (nettoyage auto à 200 points).
- **Verrouillage à distance** : la direction pose `lock=true` → `lockNow()`
  via DeviceAdmin, avec acquittement (`lock=false`).
- **SMS de secours** : si Firestore injoignable, SMS avec position + lien
  Maps au numéro de secours (champ facultatif, permission `SEND_SMS`
  conditionnelle, anti-spam 1/10 min).
- **Règles Firestore** : collection `staff/{uid}` (direction), la direction
  ne peut modifier que `status` + `lock`, lit appareils + historique.
- **Design** : splash screen animé (`SplashActivity`, 1,2 s), icône
  adaptative (bouclier + cadenas, fond rouge LEX), version 1.4.
