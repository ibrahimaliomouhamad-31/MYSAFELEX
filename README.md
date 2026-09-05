# MYSAFELEX

Application anti-vol pour les téléphones des élèves de l'internat du lycée
d'excellence de Tessaoua.

## ⚠️ Étapes à faire manuellement (ne peuvent pas être automatisées depuis le code)

Ces trois actions sont **indispensables** avant tout déploiement réel, en
complément des correctifs de code déjà appliqués :

1. **Déployer les règles de sécurité.**
   Copiez le contenu de `firestore.rules` dans Firebase Console → Firestore
   Database → Règles, et celui de `storage.rules` dans Storage → Règles.
   Sans ça, l'authentification ajoutée dans le code ne sert à rien : la base
   reste ouverte tant que les règles ne sont pas publiées côté serveur.

2. **Restreindre la clé API Android.**
   Dans Google Cloud Console → Identifiants, limitez la clé API présente
   dans `google-services.json` au nom de package `com.mysafelex` et à
   l'empreinte SHA-1 de votre certificat de signature.

3. **Rendre ce dépôt privé** (ou, si vous le gardez public, vérifiez que les
   deux points ci-dessus sont bien faits — c'est la vraie protection, pas la
   visibilité du dépôt).

## Historique des correctifs

Voir `MYSAFELEX_audit_code.md` pour le détail : authentification anonyme
Firebase, règles de sécurité Firestore/Storage, correction d'un
contournement de code PIN, protection contre le détournement de matricule,
migration de la caméra vers CameraX, gestion des permissions refusées, et
texte honnête du dialogue de configuration (l'app annonçait à tort que
"Android exige" l'épinglage).
