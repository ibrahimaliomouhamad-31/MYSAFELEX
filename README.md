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

## Historique des correctifs

Voir `MYSAFELEX_audit_code.md` pour le détail : authentification anonyme
Firebase, règles de sécurité Firestore, correction d'un contournement de
code PIN, protection contre le détournement de matricule, migration de la
caméra vers CameraX, passage des photos en Base64/Firestore (suppression de
Firebase Storage et de l'audio pour rester sur le forfait gratuit), gestion
des permissions refusées, et texte honnête du dialogue de configuration.
